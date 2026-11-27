package in.FormFlow.demo;

import in.FormFlow.demo.Entity.AdminInvitation;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.Repository.AdminInvitationRepository;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.config.TokenHashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlatformOnboardingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminInvitationRepository adminInvitationRepository;

    @Autowired
    private UserRepository userRepository;

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private String createOrganization(String superAdminToken, String name) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/platform/organizations")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "description": "Org desc" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is(name)))
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asString();
    }

    private String createInvitation(String superAdminToken, String orgId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/platform/organizations/{orgId}/invitations", orgId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId", is(orgId)))
                .andExpect(jsonPath("$.invitationToken", notNullValue()))
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("invitationToken").asString();
    }

    @Test
    void superAdminHasNoOrganizationAndCannotUseOrgAdminEndpoints() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "owner@formflow.test", "password": "OwnerPass123!" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("SUPER_ADMIN")))
                .andExpect(jsonPath("$.organizationId", nullValue()))
                .andReturn();

        String superToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asString();

        mockMvc.perform(get("/api/forms")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reviewers")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reviewer/submissions")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicAdminRegistrationWithoutTokenFails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Unauthorized Admin",
                                  "email": "noinvite@example.com",
                                  "password": "Password123!",
                                  "organizationName": "Rogue Org"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validInvitationRegistersAdminAndStoresOnlySha256Hash() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String orgId = createOrganization(superToken, "Acme Corp");
        String rawToken = createInvitation(superToken, orgId);

        AdminInvitation stored = adminInvitationRepository.findAll().get(0);
        assertNotEquals(rawToken, stored.getTokenHash());
        assertEquals(TokenHashUtils.sha256Hex(rawToken), stored.getTokenHash());
        assertNull(stored.getUsedAt());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Admin",
                                  "email": "admin@acme.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.organizationId", is(orgId)))
                .andExpect(jsonPath("$.organizationName", is("Acme Corp")));

        AdminInvitation afterUse = adminInvitationRepository.findById(stored.getId()).orElseThrow();
        assertNotNull(afterUse.getUsedAt());
    }

    @Test
    void invalidExpiredAndReusedInvitationsAreRejected() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String orgId = createOrganization(superToken, "Beta Corp");

        // 1. Invalid token
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "User",
                                  "email": "invalid@beta.com",
                                  "password": "Password123!",
                                  "invitationToken": "completely-invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // 2. Expired token
        String expiredRawToken = createInvitation(superToken, orgId);
        AdminInvitation expiredInv = adminInvitationRepository
                .findByTokenHash(TokenHashUtils.sha256Hex(expiredRawToken))
                .orElseThrow();
        expiredInv.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        adminInvitationRepository.save(expiredInv);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "User",
                                  "email": "expired@beta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(expiredRawToken)))
                .andExpect(status().isGone());

        // 3. Reused token
        String validToken = createInvitation(superToken, orgId);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "First Admin",
                                  "email": "first@beta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(validToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Second Admin",
                                  "email": "second@beta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(validToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void registrationIgnoresClientSuppliedOrganizationAndRole() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String invitedOrgId = createOrganization(superToken, "Invited Org");
        String otherOrgId = createOrganization(superToken, "Other Org");
        String rawToken = createInvitation(superToken, invitedOrgId);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sneaky Admin",
                                  "email": "sneaky@invited.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s",
                                  "organizationId": "%s",
                                  "role": "SUPER_ADMIN"
                                }
                                """.formatted(rawToken, otherOrgId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.organizationId", is(invitedOrgId)))
                .andExpect(jsonPath("$.organizationName", is("Invited Org")));
    }

    @Test
    void adminAndReviewerCannotAccessPlatformEndpoints() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String orgId = createOrganization(superToken, "Gamma Org");
        String rawToken = createInvitation(superToken, orgId);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gamma Admin",
                                  "email": "admin@gamma.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isCreated());

        String adminToken = login("admin@gamma.com", "Password123!");

        mockMvc.perform(post("/api/admin/reviewers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Gamma Rev", "email": "rev@gamma.com", "password": "Password123!" }
                                """))
                .andExpect(status().isCreated());

        String reviewerToken = login("rev@gamma.com", "Password123!");

        // ADMIN blocked from creating organizations or invitations
        mockMvc.perform(post("/api/platform/organizations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Illegal Org" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/platform/organizations/{orgId}/invitations", orgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // REVIEWER blocked from platform endpoints and reviewer creation
        mockMvc.perform(post("/api/platform/organizations")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Illegal Org 2" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/platform/organizations/{orgId}/invitations", orgId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/reviewers")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Unauthorized Rev", "email": "rev2@gamma.com", "password": "Password123!" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedRegistrationRollsBackWithoutConsumingInvitation() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String orgId = createOrganization(superToken, "Delta Org");
        String firstToken = createInvitation(superToken, orgId);
        String secondToken = createInvitation(superToken, orgId);

        // Register first user with duplicate email target
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Existing User",
                                  "email": "dup@delta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(firstToken)))
                .andExpect(status().isCreated());

        // Attempt registration with secondToken using already-registered email -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate Attempt",
                                  "email": "dup@delta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(secondToken)))
                .andExpect(status().isConflict());

        // secondToken must still be unused and work with a fresh email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Second Valid Admin",
                                  "email": "valid2@delta.com",
                                  "password": "Password123!",
                                  "invitationToken": "%s"
                                }
                                """.formatted(secondToken)))
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentRegistrationWithSameInvitationAllowsOnlyOneSuccess() throws Exception {
        String superToken = login("owner@formflow.test", "OwnerPass123!");
        String orgId = createOrganization(superToken, "Concurrency Org");
        String rawToken = createInvitation(superToken, orgId);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                tasks.add(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "name": "Concurrent Admin %d",
                                              "email": "concurrent%d@org.com",
                                              "password": "Password123!",
                                              "invitationToken": "%s"
                                            }
                                            """.formatted(idx, idx, rawToken)))
                            .andReturn();
                    return res.getResponse().getStatus();
                });
            }

            Future<Integer> f1 = pool.submit(tasks.get(0));
            Future<Integer> f2 = pool.submit(tasks.get(1));

            readyLatch.await();
            startLatch.countDown();

            List<Integer> statuses = List.of(f1.get(), f2.get());
            long createdCount = statuses.stream().filter(s -> s == 201).count();
            long conflictCount = statuses.stream().filter(s -> s == 409).count();

            assertEquals(1, createdCount, "Exactly one concurrent request should succeed, statuses=" + statuses);
            assertEquals(1, conflictCount, "Second concurrent request should be rejected with 409, statuses=" + statuses);

            List<User> orgAdmins = userRepository.findAllByOrganization_Id(UUID.fromString(orgId));
            assertEquals(1, orgAdmins.size());
        } finally {
            pool.shutdownNow();
        }
    }
}

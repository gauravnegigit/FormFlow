package in.FormFlow.demo;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FormFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SCHEMA_JSON = """
            {
              "fields": [
                { "id": "fullName", "label": "Full Name", "type": "text", "required": true },
                { "id": "email", "label": "Email", "type": "email", "required": true },
                { "id": "track", "label": "Track", "type": "select", "required": true,
                  "options": [
                    { "label": "Backend", "value": "backend" },
                    { "label": "Frontend", "value": "frontend" }
                  ]
                },
                { "id": "agree", "label": "Agree", "type": "checkbox", "required": true }
              ]
            }
            """;

    private String login(String email, String password) throws Exception {
        String body = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asString();
    }

    private void onboardAndRegisterAdmin(String name, String email, String password, String orgName) throws Exception {
        String superAdminToken = login("owner@formflow.test", "OwnerPass123!");

        MvcResult orgResult = mockMvc.perform(post("/api/platform/organizations")
                        .header("Authorization", "Bearer " + superAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "description": "Test org" }
                                """.formatted(orgName)))
                .andExpect(status().isCreated())
                .andReturn();

        String orgId = objectMapper.readTree(orgResult.getResponse().getContentAsString()).get("id").asString();

        MvcResult invResult = mockMvc.perform(post("/api/platform/organizations/{orgId}/invitations", orgId)
                        .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isCreated())
                .andReturn();

        String invitationToken = objectMapper.readTree(invResult.getResponse().getContentAsString())
                .get("invitationToken").asString();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "invitationToken": "%s"
                                }
                                """.formatted(name, email, password, invitationToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.organizationName", is(orgName)));
    }

    private JsonNode createForm(String token, String title) throws Exception {
        String body = """
                {
                  "title": "%s",
                  "description": "Integration test form",
                  "schema": %s
                }
                """.formatted(title, SCHEMA_JSON);

        MvcResult result = mockMvc.perform(post("/api/forms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode publishForm(String token, String formId) throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/forms/{id}/publish", formId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createReviewer(String adminToken, String name, String email, String password) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(name, email, password);

        MvcResult result = mockMvc.perform(post("/api/admin/reviewers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("REVIEWER")))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode submitPublic(String slug, String fullName) throws Exception {
        String body = """
                {
                  "category": "engineering",
                  "answers": {
                    "fullName": "%s",
                    "email": "applicant@example.com",
                    "track": "backend",
                    "agree": true
                  }
                }
                """.formatted(fullName);

        MvcResult result = mockMvc.perform(post("/api/submissions/public/{slug}", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.trackingToken", notNullValue()))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void fullEndToEndApprovalAndTrackingWorkflow() throws Exception {
        onboardAndRegisterAdmin("Admin A", "admina@orga.com", "Password123!", "OrgA");
        String adminToken = login("admina@orga.com", "Password123!");

        JsonNode form = createForm(adminToken, "Backend Internship");
        String formId = form.get("id").asString();
        JsonNode published = publishForm(adminToken, formId);
        String slug = published.get("publicSlug").asString();

        mockMvc.perform(get("/api/forms/public/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Backend Internship")));

        JsonNode submission = submitPublic(slug, "Alice Applicant");
        String submissionId = submission.get("id").asString();
        String trackingToken = submission.get("trackingToken").asString();

        mockMvc.perform(get("/api/submissions/track/{token}", trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.formTitle", is("Backend Internship")));

        JsonNode reviewer = createReviewer(adminToken, "Reviewer A", "reva@orga.com", "RevPass123!");
        String reviewerId = reviewer.get("id").asString();

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", submissionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reviewerId": "%s" }
                                """.formatted(reviewerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedReviewerId", is(reviewerId)));

        String reviewerToken = login("reva@orga.com", "RevPass123!");

        mockMvc.perform(get("/api/reviewer/submissions")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(submissionId)));

        mockMvc.perform(get("/api/reviewer/submissions/{id}", submissionId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.fullName", is("Alice Applicant")));

        mockMvc.perform(patch("/api/reviewer/submissions/{id}/approve", submissionId)
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(get("/api/submissions/track/{token}", trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    void multiTenancyIsolatesFormsSubmissionsAndReviewers() throws Exception {
        onboardAndRegisterAdmin("Admin A", "admina@orga.com", "Password123!", "OrgA");
        onboardAndRegisterAdmin("Admin B", "adminb@orgb.com", "Password123!", "OrgB");
        String tokenA = login("admina@orga.com", "Password123!");
        String tokenB = login("adminb@orgb.com", "Password123!");

        JsonNode formA = publishForm(tokenA, createForm(tokenA, "OrgA Form").get("id").asString());
        String formAId = formA.get("id").asString();
        JsonNode subA = submitPublic(formA.get("publicSlug").asString(), "OrgA Candidate");
        String subAId = subA.get("id").asString();

        JsonNode reviewerB = createReviewer(tokenB, "Reviewer B", "revb@orgb.com", "RevPass123!");

        mockMvc.perform(get("/api/forms")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/forms/{id}", formAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/forms/{id}/submissions", formAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/submissions/{id}", subAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", subAId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reviewerId": "%s" }
                                """.formatted(reviewerB.get("id").asString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleRestrictionsAndUnauthenticatedAccessAreBlocked() throws Exception {
        onboardAndRegisterAdmin("Admin A", "admina@orga.com", "Password123!", "OrgA");
        String adminToken = login("admina@orga.com", "Password123!");
        createReviewer(adminToken, "Reviewer A", "reva@orga.com", "RevPass123!");
        String reviewerToken = login("reva@orga.com", "RevPass123!");

        mockMvc.perform(get("/api/forms"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reviewer/submissions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/platform/organizations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/forms")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reviewers")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reviewer/submissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidStateTransitionsReturn409AndBadAnswersReturn400() throws Exception {
        onboardAndRegisterAdmin("Admin A", "admina@orga.com", "Password123!", "OrgA");
        String adminToken = login("admina@orga.com", "Password123!");
        JsonNode reviewer = createReviewer(adminToken, "Reviewer A", "reva@orga.com", "RevPass123!");

        JsonNode form = publishForm(adminToken, createForm(adminToken, "Strict Form").get("id").asString());
        String formId = form.get("id").asString();
        String slug = form.get("publicSlug").asString();

        mockMvc.perform(patch("/api/forms/{id}/publish", formId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/forms/{id}", formId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/submissions/public/{slug}", slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": {
                                    "fullName": "Bob",
                                    "email": "bob@example.com",
                                    "track": "backend",
                                    "agree": true,
                                    "hackedField": "nope"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        String subId = submitPublic(slug, "Bob").get("id").asString();
        String reviewerId = reviewer.get("id").asString();

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", subId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reviewerId": "%s" }
                                """.formatted(reviewerId)))
                .andExpect(status().isOk());

        String reviewerToken = login("reva@orga.com", "RevPass123!");
        mockMvc.perform(patch("/api/reviewer/submissions/{id}/reject", subId)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rejectionReason": "Missing portfolio" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.rejectionReason", is("Missing portfolio")));

        mockMvc.perform(patch("/api/admin/submissions/{id}/assign", subId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reviewerId": "%s" }
                                """.formatted(reviewerId)))
                .andExpect(status().isConflict());
    }

    @Test
    void paginationAndStatusFilteringWorkCorrectly() throws Exception {
        onboardAndRegisterAdmin("Admin A", "admina@orga.com", "Password123!", "OrgA");
        String adminToken = login("admina@orga.com", "Password123!");

        JsonNode f1 = createForm(adminToken, "Form 1");
        createForm(adminToken, "Form 2");
        createForm(adminToken, "Form 3");
        publishForm(adminToken, f1.get("id").asString());

        mockMvc.perform(get("/api/forms")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.first", is(true)))
                .andExpect(jsonPath("$.last", is(false)));

        mockMvc.perform(get("/api/forms")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }
}

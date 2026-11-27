package in.FormFlow.demo.Service;

import in.FormFlow.demo.Entity.AdminInvitation;
import in.FormFlow.demo.Entity.Organization;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.Repository.AdminInvitationRepository;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.config.TokenHashUtils;
import in.FormFlow.demo.dto.auth.AuthResponse;
import in.FormFlow.demo.dto.auth.LoginRequest;
import in.FormFlow.demo.dto.auth.RegisterRequest;
import in.FormFlow.demo.dto.auth.UserResponse;
import in.FormFlow.demo.dto.form.CreateReviewerRequest;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.exception.DuplicateEmailException;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final AdminInvitationRepository adminInvitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            AdminInvitationRepository adminInvitationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.adminInvitationRepository = adminInvitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String rawToken = request.invitationToken().trim();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        LocalDateTime now = LocalDateTime.now();

        // 1. Lock the invitation row to prevent concurrent registrations from using the same token
        AdminInvitation invitation = adminInvitationRepository.findForUpdateByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid invitation token"
                ));

        // 2. Reject already-used invitations
        if (invitation.getUsedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invitation token has already been used"
            );
        }

        // 3. Reject expired invitations
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Invitation token has expired"
            );
        }

        // 4. Reject duplicate emails without consuming the invitation
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered");
        }

        Organization organization = invitation.getOrganization();
        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invitation is not associated with an organization"
            );
        }

        // 5. Assign organization and ADMIN role strictly server-side
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ADMIN);
        user.setOrganization(organization);

        User savedUser = userRepository.saveAndFlush(user);

        // 6. Atomically mark invitation used only after user persistence succeeds
        int updated = adminInvitationRepository.markUsedIfAvailable(invitation.getId(), now, now);
        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invitation token is no longer valid"
            );
        }

        log.info("Registered ADMIN userId={} for organizationId={} using invitationId={}",
                savedUser.getId(), organization.getId(), invitation.getId());

        return mapToUserResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        // 1. Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password")
                );

        // 2. Verify password
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // 3. Generate JWT
        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
        String orgName = user.getOrganization() != null ? user.getOrganization().getName() : null;

        // 4. Return authentication response
        return new AuthResponse(
                token,
                "Bearer",
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                orgId,
                orgName
        );
    }

    @Transactional
    public UserResponse createReviewer(CreateReviewerRequest request, Authentication authentication) {

        User admin = getAuthenticatedAdmin(authentication);

        Organization org = admin.getOrganization();
        if (org == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin does not belong to an organization");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered");
        }

        User reviewer = new User();
        reviewer.setName(request.name().trim());
        reviewer.setEmail(email);
        reviewer.setPassword(passwordEncoder.encode(request.password()));
        reviewer.setRole(UserRole.REVIEWER);
        reviewer.setOrganization(org);

        User saved = userRepository.save(reviewer);
        return mapToUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getOrganizationReviewers(Authentication authentication) {

        User admin = getAuthenticatedAdmin(authentication);

        if (admin.getOrganization() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin does not belong to an organization");
        }

        List<User> reviewers = userRepository.findAllByOrganization_IdAndRole(
                admin.getOrganization().getId(),
                UserRole.REVIEWER
        );

        return reviewers.stream()
                .map(this::mapToUserResponse)
                .toList();
    }

    private User getAuthenticatedAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        UUID adminId = UUID.fromString(authentication.getName());
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        if (admin.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can perform this action");
        }

        return admin;
    }

    private UserResponse mapToUserResponse(User user) {
        UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
        String orgName = user.getOrganization() != null ? user.getOrganization().getName() : null;

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                orgId,
                orgName
        );
    }
}

package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.auth.AuthResponse;
import in.FormFlow.demo.dto.auth.LoginRequest;
import in.FormFlow.demo.dto.auth.RegisterRequest;
import in.FormFlow.demo.dto.auth.UserResponse;
import in.FormFlow.demo.dto.form.CreateReviewerRequest;
import in.FormFlow.demo.Entity.Organization;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.exception.DuplicateEmailException;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import in.FormFlow.demo.Repository.OrganizationRepository;
import in.FormFlow.demo.Repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();

        // 1. Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered");
        }

        // 2. Sensible organization onboarding:
        // Create an organization for the registering Admin
        String orgName = (request.organizationName() != null && !request.organizationName().trim().isBlank())
                ? request.organizationName().trim()
                : request.name().trim() + "'s Organization";

        Organization organization = new Organization(orgName, "Primary workspace for " + orgName);
        organization = organizationRepository.save(organization);

        // 3. Create a new User entity (Admin)
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ADMIN);
        user.setOrganization(organization);

        // 4. Save user in database
        User savedUser = userRepository.save(user);

        // 5. Convert Entity into Response DTO
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

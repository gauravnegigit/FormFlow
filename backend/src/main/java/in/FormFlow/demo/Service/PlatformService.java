package in.FormFlow.demo.Service;

import in.FormFlow.demo.Entity.AdminInvitation;
import in.FormFlow.demo.Entity.Organization;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.Repository.AdminInvitationRepository;
import in.FormFlow.demo.Repository.OrganizationRepository;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.config.TokenHashUtils;
import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.platform.AdminInvitationResponse;
import in.FormFlow.demo.dto.platform.CreateAdminInvitationRequest;
import in.FormFlow.demo.dto.platform.CreateOrganizationRequest;
import in.FormFlow.demo.dto.platform.OrganizationResponse;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);
    private static final int DEFAULT_EXPIRY_HOURS = 24;

    private final OrganizationRepository organizationRepository;
    private final AdminInvitationRepository adminInvitationRepository;
    private final UserRepository userRepository;

    public PlatformService(
            OrganizationRepository organizationRepository,
            AdminInvitationRepository adminInvitationRepository,
            UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.adminInvitationRepository = adminInvitationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        User superAdmin = getAuthenticatedSuperAdmin();

        String normalizedName = request.name().trim();
        if (organizationRepository.existsByName(normalizedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization with this name already exists");
        }

        String description = (request.description() != null && !request.description().isBlank())
                ? request.description().trim()
                : null;

        Organization organization = new Organization(normalizedName, description);
        Organization saved = organizationRepository.save(organization);

        log.info("Organization created id={} by superAdminId={}", saved.getId(), superAdmin.getId());
        return mapToOrganizationResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> listOrganizations(Pageable pageable) {
        getAuthenticatedSuperAdmin();
        Pageable safePageable = PageResponse.clamp(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Organization> page = organizationRepository.findAll(safePageable);
        return PageResponse.from(page, this::mapToOrganizationResponse);
    }

    @Transactional
    public AdminInvitationResponse createAdminInvitation(
            UUID organizationId,
            CreateAdminInvitationRequest request) {

        User superAdmin = getAuthenticatedSuperAdmin();

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        int expiryHours = (request != null && request.expiresInHours() != null)
                ? request.expiresInHours()
                : DEFAULT_EXPIRY_HOURS;

        String rawToken = TokenHashUtils.generateRawToken();
        String tokenHash = TokenHashUtils.sha256Hex(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(expiryHours);

        AdminInvitation invitation = new AdminInvitation();
        invitation.setOrganization(organization);
        invitation.setCreatedBy(superAdmin);
        invitation.setTokenHash(tokenHash);
        invitation.setExpiresAt(expiresAt);

        AdminInvitation saved = adminInvitationRepository.save(invitation);

        log.info("Admin invitation generated id={} organizationId={} expiresAt={}",
                saved.getId(), organization.getId(), saved.getExpiresAt());

        return new AdminInvitationResponse(
                saved.getId(),
                organization.getId(),
                organization.getName(),
                rawToken,
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    private User getAuthenticatedSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != UserRole.SUPER_ADMIN || user.getOrganization() != null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only platform-level SUPER_ADMIN accounts can perform this action"
            );
        }

        return user;
    }

    private OrganizationResponse mapToOrganizationResponse(Organization org) {
        return new OrganizationResponse(
                org.getId(),
                org.getName(),
                org.getDescription(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}

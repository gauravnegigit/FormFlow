package in.FormFlow.demo.Controller;

import in.FormFlow.demo.Service.PlatformService;
import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.platform.AdminInvitationResponse;
import in.FormFlow.demo.dto.platform.CreateAdminInvitationRequest;
import in.FormFlow.demo.dto.platform.CreateOrganizationRequest;
import in.FormFlow.demo.dto.platform.OrganizationResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformController {

    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {

        OrganizationResponse response = platformService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/organizations")
    public ResponseEntity<PageResponse<OrganizationResponse>> listOrganizations(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(platformService.listOrganizations(pageable));
    }

    @PostMapping("/organizations/{organizationId}/invitations")
    public ResponseEntity<AdminInvitationResponse> createAdminInvitation(
            @PathVariable("organizationId") UUID organizationId,
            @Valid @RequestBody(required = false) CreateAdminInvitationRequest request) {

        AdminInvitationResponse response = platformService.createAdminInvitation(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

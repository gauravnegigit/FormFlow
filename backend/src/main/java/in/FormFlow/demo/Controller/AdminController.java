package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.auth.UserResponse;
import in.FormFlow.demo.dto.form.CreateReviewerRequest;
import in.FormFlow.demo.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviewers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createReviewer(
            @Valid @RequestBody CreateReviewerRequest request,
            Authentication authentication) {

        UserResponse response = authService.createReviewer(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getOrganizationReviewers(
            Authentication authentication) {

        List<UserResponse> reviewers = authService.getOrganizationReviewers(authentication);
        return ResponseEntity.ok(reviewers);
    }
}

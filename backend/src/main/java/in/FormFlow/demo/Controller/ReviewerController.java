package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.form.RejectSubmissionRequest;
import in.FormFlow.demo.dto.form.ReviewerSubmissionResponse;
import in.FormFlow.demo.enums.SubmissionStatus;
import in.FormFlow.demo.Service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviewer")
@PreAuthorize("hasRole('REVIEWER')")
public class ReviewerController {

    private final SubmissionService submissionService;

    public ReviewerController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/submissions")
    public ResponseEntity<PageResponse<ReviewerSubmissionResponse>> getAssignedSubmissions(
            @RequestParam(name = "status", required = false) SubmissionStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                submissionService.getAssignedSubmissions(status, pageable)
        );
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<ReviewerSubmissionResponse> getAssignedSubmissionById(
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(
                submissionService.getAssignedSubmissionById(id)
        );
    }

    @PatchMapping("/submissions/{id}/approve")
    public ResponseEntity<ReviewerSubmissionResponse> approveSubmission(
            @PathVariable("id") UUID id) {

        return ResponseEntity.ok(
                submissionService.approveSubmission(id)
        );
    }

    @PatchMapping("/submissions/{id}/reject")
    public ResponseEntity<ReviewerSubmissionResponse> rejectSubmission(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RejectSubmissionRequest request) {

        return ResponseEntity.ok(
                submissionService.rejectSubmission(id, request)
        );
    }
}

package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.AssignReviewerRequest;
import in.FormFlow.demo.dto.form.SubmissionDetailResponse;
import in.FormFlow.demo.Service.SubmissionService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/submissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubmissionController {

    private final SubmissionService submissionService;

    public AdminSubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<SubmissionDetailResponse> assignReviewer(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignReviewerRequest request) {

        SubmissionDetailResponse response =
                submissionService.assignReviewer(
                        id,
                        request.reviewerId()
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/form/{formId}")
    public ResponseEntity<List<AdminSubmissionResponse>> getFormSubmissions(
            @PathVariable("formId") UUID formId) {

        List<AdminSubmissionResponse> responses =
                submissionService.getFormSubmissions(formId);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDetailResponse> getSubmissionById(
            @PathVariable("id") UUID id) {

        SubmissionDetailResponse response =
                submissionService.getSubmissionById(id);

        return ResponseEntity.ok(response);
    }
}

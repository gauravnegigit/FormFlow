package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.AssignReviewerRequest;
import in.FormFlow.demo.dto.form.SubmissionDetailResponse;
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
    public ResponseEntity<PageResponse<AdminSubmissionResponse>> getFormSubmissions(
            @PathVariable("formId") UUID formId,
            @RequestParam(name = "status", required = false) SubmissionStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AdminSubmissionResponse> responses =
                submissionService.getFormSubmissions(formId, status, pageable);

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

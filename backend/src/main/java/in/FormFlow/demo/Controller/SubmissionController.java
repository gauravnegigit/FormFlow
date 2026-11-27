package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.CreateSubmissionRequest;
import in.FormFlow.demo.dto.form.PublicSubmissionTrackResponse;
import in.FormFlow.demo.dto.form.SubmissionDetailResponse;
import in.FormFlow.demo.dto.form.SubmissionResponse;
import in.FormFlow.demo.enums.SubmissionStatus;
import in.FormFlow.demo.Service.SubmissionService;

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
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/public/{slug}")
    public ResponseEntity<SubmissionResponse> submitForm(
            @PathVariable("slug") String slug,
            @Valid @RequestBody CreateSubmissionRequest request) {

        SubmissionResponse response =
                submissionService.submitForm(slug, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/track/{trackingToken}")
    public ResponseEntity<PublicSubmissionTrackResponse> trackSubmission(
            @PathVariable("trackingToken") String trackingToken) {

        PublicSubmissionTrackResponse response =
                submissionService.trackSubmission(trackingToken);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/form/{formId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<AdminSubmissionResponse>> getFormSubmissions(
            @PathVariable("formId") UUID formId,
            @RequestParam(name = "status", required = false) SubmissionStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AdminSubmissionResponse> responses =
                submissionService.getFormSubmissions(formId, status, pageable);

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubmissionDetailResponse> getSubmissionById(
            @PathVariable("id") UUID id) {

        SubmissionDetailResponse response =
                submissionService.getSubmissionById(id);

        return ResponseEntity.ok(response);
    }
}

package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.CreateSubmissionRequest;
import in.FormFlow.demo.dto.form.SubmissionResponse;
import in.FormFlow.demo.Service.SubmissionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            @PathVariable String slug,
            @Valid @RequestBody CreateSubmissionRequest request) {

        SubmissionResponse response =
                submissionService.submitForm(slug, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/form/{formId}")
    public ResponseEntity<List<AdminSubmissionResponse>> getFormSubmissions(
            @PathVariable UUID formId) {

        List<AdminSubmissionResponse> responses =
                submissionService.getFormSubmissions(formId);

        return ResponseEntity.ok(responses);
    }
}

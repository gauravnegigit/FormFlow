package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.CreateFormRequest;
import in.FormFlow.demo.dto.form.FormResponse;
import in.FormFlow.demo.dto.form.PublicFormResponse;
import in.FormFlow.demo.dto.form.UpdateFormRequest;
import in.FormFlow.demo.Service.FormService;
import in.FormFlow.demo.Service.SubmissionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    private final FormService formService;
    private final SubmissionService submissionService;

    public FormController(FormService formService, SubmissionService submissionService) {
        this.formService = formService;
        this.submissionService = submissionService;
    }

    // Create a new form
    @PostMapping
    public ResponseEntity<FormResponse> createForm(
            @Valid @RequestBody CreateFormRequest request,
            Authentication authentication) {

        FormResponse response = formService.createForm(request, authentication);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FormResponse>> getMyForms() {

        List<FormResponse> forms = formService.getMyForms();

        return ResponseEntity.ok(forms);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormResponse> getFormById(
            @PathVariable UUID id) {

        FormResponse response = formService.getFormById(id);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormResponse> updateForm(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFormRequest request) {

        FormResponse response = formService.updateForm(id, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteForm(@PathVariable UUID id) {

        formService.deleteForm(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<FormResponse> publishForm(
            @PathVariable UUID id) {

        FormResponse response = formService.publishForm(id);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<FormResponse> closeForm(
            @PathVariable UUID id) {

        FormResponse response = formService.closeForm(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/public/{slug}")
    public ResponseEntity<PublicFormResponse> getPublicForm(
            @PathVariable String slug) {

        PublicFormResponse response =
                formService.getPublicForm(slug);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/submissions")
    public ResponseEntity<List<AdminSubmissionResponse>> getFormSubmissions(
            @PathVariable UUID id) {

        List<AdminSubmissionResponse> submissions =
                submissionService.getFormSubmissions(id);

        return ResponseEntity.ok(submissions);
    }
}

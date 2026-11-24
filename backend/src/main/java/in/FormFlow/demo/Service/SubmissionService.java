package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.CreateSubmissionRequest;
import in.FormFlow.demo.dto.form.SubmissionResponse;
import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.Entity.Submission;
import in.FormFlow.demo.enums.FormStatus;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import in.FormFlow.demo.Repository.FormRepository;
import in.FormFlow.demo.Repository.SubmissionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

@Service
public class SubmissionService {

    private final FormRepository formRepository;
    private final SubmissionRepository submissionRepository;

    public SubmissionService(
            FormRepository formRepository,
            SubmissionRepository submissionRepository) {
        this.formRepository = formRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public SubmissionResponse submitForm(
            String slug,
            CreateSubmissionRequest request) {

        Form form = formRepository
                .findByPublicSlugAndStatus(slug, FormStatus.PUBLISHED)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Form not found or closed"));

        JsonNode answers = request.answers();

        validateAnswers(form.getSchema(), answers);

        Submission submission = new Submission();
        submission.setForm(form);
        submission.setAnswers(answers);

        Submission saved = submissionRepository.save(submission);

        return new SubmissionResponse(
                saved.getId(),
                saved.getStatus(),
                saved.getTrackingToken(),
                saved.getSubmittedAt()
        );
    }

    private void validateAnswers(JsonNode schema, JsonNode answers) {

        if (answers == null || !answers.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Answers must be a JSON object");
        }

        JsonNode fields = schema.get("fields");

        for (JsonNode field : fields) {

            String fieldId = field.get("id").asString();
            boolean required = field.get("required").asBoolean();

            if (required) {

                JsonNode value = answers.get(fieldId);

                if (value == null || value.isNull()
                        || (value.isString() && value.asString().isBlank())) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " is required");
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AdminSubmissionResponse> getFormSubmissions(UUID formId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        UUID userId = UUID.fromString(authentication.getName());

        // Verify that this form belongs to the logged-in admin
        formRepository.findByIdAndCreatedBy_Id(formId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        List<Submission> submissions =
                submissionRepository.findAllByForm_Id(formId);

        return submissions.stream()
                .map(submission -> new AdminSubmissionResponse(
                        submission.getId(),
                        submission.getAnswers(),
                        submission.getStatus(),
                        submission.getSubmittedAt()
                ))
                .toList();
    }
}

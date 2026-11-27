package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.form.AdminSubmissionResponse;
import in.FormFlow.demo.dto.form.CreateSubmissionRequest;
import in.FormFlow.demo.dto.form.PublicSubmissionTrackResponse;
import in.FormFlow.demo.dto.form.RejectSubmissionRequest;
import in.FormFlow.demo.dto.form.ReviewerSubmissionResponse;
import in.FormFlow.demo.dto.form.SubmissionDetailResponse;
import in.FormFlow.demo.dto.form.SubmissionResponse;
import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.Entity.Submission;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.FormStatus;
import in.FormFlow.demo.enums.SubmissionStatus;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import in.FormFlow.demo.Repository.FormRepository;
import in.FormFlow.demo.Repository.SubmissionRepository;
import in.FormFlow.demo.Repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SubmissionService {

    private final FormRepository formRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    public SubmissionService(
            FormRepository formRepository,
            SubmissionRepository submissionRepository,
            UserRepository userRepository) {
        this.formRepository = formRepository;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
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
        // Multi-tenancy: Organization must be derived from the associated form, not trusted from applicant input
        submission.setOrganization(form.getOrganization());
        submission.setAnswers(answers);
        if (request.category() != null && !request.category().trim().isBlank()) {
            submission.setCategory(request.category().trim());
        }

        Submission saved = submissionRepository.save(submission);

        return new SubmissionResponse(
                saved.getId(),
                saved.getStatus(),
                saved.getTrackingToken(),
                saved.getSubmittedAt()
        );
    }


    @Transactional(readOnly = true)
    public PublicSubmissionTrackResponse trackSubmission(String trackingToken) {

        if (trackingToken == null || trackingToken.isBlank()) {
            throw new ResourceNotFoundException("Submission not found for tracking token");
        }

        Submission submission = submissionRepository
                .findByTrackingToken(trackingToken.trim())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found for tracking token"));

        String rejectionReason = submission.getStatus() == SubmissionStatus.REJECTED
                ? submission.getRejectionReason()
                : null;

        return new PublicSubmissionTrackResponse(
                submission.getId(),
                submission.getForm().getTitle(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                rejectionReason
        );
    }

    @Transactional(readOnly = true)
    public List<AdminSubmissionResponse> getFormSubmissions(UUID formId) {

        User admin = getAuthenticatedAdmin();
        UUID orgId = admin.getOrganization().getId();

        // 1. Verify that this form belongs to the authenticated admin's organization
        formRepository.findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        // 2. Fetch submissions scoped to this form and organization
        List<Submission> submissions =
                submissionRepository.findAllByForm_IdAndOrganization_Id(formId, orgId);

        return submissions.stream()
                .map(this::mapToAdminSubmissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmissionById(UUID submissionId) {

        User admin = getAuthenticatedAdmin();
        UUID orgId = admin.getOrganization().getId();

        // Verify ownership through organization isolation
        Submission submission = submissionRepository
                .findByIdAndOrganization_Id(submissionId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found"));

        return mapToSubmissionDetailResponse(submission);
    }

    @Transactional
    public SubmissionDetailResponse assignReviewer(
            UUID submissionId,
            UUID reviewerId) {

        User admin = getAuthenticatedAdmin();
        UUID orgId = admin.getOrganization().getId();

        // 1. Verify submission belongs to this admin's organization
        Submission submission = submissionRepository
                .findByIdAndOrganization_Id(submissionId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found"));

        // 2. Only PENDING submissions can be assigned or reassigned
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only PENDING submissions can be assigned or reassigned"
            );
        }

        // 3. Find reviewer
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Reviewer not found"));

        // 3. Verify reviewer role
        if (reviewer.getRole() != UserRole.REVIEWER) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selected user is not a reviewer"
            );
        }

        // 4. Verify reviewer belongs to the same organization
        if (reviewer.getOrganization() == null || !reviewer.getOrganization().getId().equals(orgId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reviewer does not belong to the same organization"
            );
        }

        // 5. Update assignment
        submission.setAssignedReviewer(reviewer);
        submission.setAssignedAt(LocalDateTime.now());

        Submission saved = submissionRepository.save(submission);

        return mapToSubmissionDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReviewerSubmissionResponse> getAssignedSubmissions() {

        User reviewer = getAuthenticatedReviewer();
        UUID orgId = reviewer.getOrganization().getId();

        // Fetch only submissions assigned to this reviewer in their organization
        List<Submission> submissions =
                submissionRepository.findAllByAssignedReviewer_IdAndOrganization_Id(reviewer.getId(), orgId);

        return submissions.stream()
                .map(this::mapToReviewerSubmissionResponse)
                .toList();
    }

    @Transactional
    public ReviewerSubmissionResponse approveSubmission(UUID submissionId) {

        User reviewer = getAuthenticatedReviewer();
        UUID orgId = reviewer.getOrganization().getId();

        // Reviewer must be assigned to the submission and belong to the same organization
        Submission submission = submissionRepository
                .findByIdAndAssignedReviewer_IdAndOrganization_Id(submissionId, reviewer.getId(), orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found or not assigned to you"));

        // Only PENDING submissions can be decided
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This submission has already been reviewed"
            );
        }

        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setRejectionReason(null);

        Submission saved = submissionRepository.save(submission);

        return mapToReviewerSubmissionResponse(saved);
    }

    @Transactional
    public ReviewerSubmissionResponse rejectSubmission(
            UUID submissionId,
            RejectSubmissionRequest request) {

        if (request == null || request.rejectionReason() == null || request.rejectionReason().trim().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required"
            );
        }

        User reviewer = getAuthenticatedReviewer();
        UUID orgId = reviewer.getOrganization().getId();

        // Reviewer must be assigned to the submission and belong to the same organization
        Submission submission = submissionRepository
                .findByIdAndAssignedReviewer_IdAndOrganization_Id(submissionId, reviewer.getId(), orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found or not assigned to you"));

        // Only PENDING submissions can be decided
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This submission has already been reviewed"
            );
        }

        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setRejectionReason(request.rejectionReason().trim());
        submission.setReviewedAt(LocalDateTime.now());

        Submission saved = submissionRepository.save(submission);

        return mapToReviewerSubmissionResponse(saved);
    }

    private User getAuthenticatedAdmin() {
        User user = getAuthenticatedUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only admins can perform this action"
            );
        }
        return user;
    }

    private User getAuthenticatedReviewer() {
        User user = getAuthenticatedUser();
        if (user.getRole() != UserRole.REVIEWER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only reviewers can perform this action"
            );
        }
        return user;
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        UUID userId = UUID.fromString(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getOrganization() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User does not belong to any organization"
            );
        }

        return user;
    }

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[0-9\\s\\-().]{7,20}$");

    private void validateAnswers(JsonNode schema, JsonNode answers) {

        if (answers == null || !answers.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Answers must be a JSON object");
        }

        if (schema == null || !schema.isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid form schema");
        }

        JsonNode fields = schema.get("fields");
        if (fields == null || !fields.isArray() || fields.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Form schema has no fields");
        }

        Map<String, JsonNode> schemaFieldsById = new LinkedHashMap<>();
        for (JsonNode field : fields) {
            if (field == null || !field.isObject() || !field.hasNonNull("id") || !field.get("id").isString()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid form schema field definition");
            }
            schemaFieldsById.put(field.get("id").asString().trim(), field);
        }

        // Reject unexpected fields not defined in the form schema
        for (String answerKey : answers.propertyNames()) {
            if (!schemaFieldsById.containsKey(answerKey)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unexpected field in answers: " + answerKey);
            }
        }

        for (Map.Entry<String, JsonNode> entry : schemaFieldsById.entrySet()) {
            String fieldId = entry.getKey();
            JsonNode field = entry.getValue();

            String fieldType = field.hasNonNull("type") && field.get("type").isString()
                    ? field.get("type").asString().trim().toLowerCase()
                    : "text";
            boolean required = field.hasNonNull("required") && field.get("required").asBoolean();

            JsonNode value = answers.get(fieldId);

            if (value == null || value.isNull()) {
                if (required) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " is required");
                }
                continue;
            }

            if (value.isObject()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        fieldId + " cannot be a JSON object");
            }

            validateFieldValue(fieldId, fieldType, required, field, value);
        }
    }

    private void validateFieldValue(
            String fieldId,
            String fieldType,
            boolean required,
            JsonNode field,
            JsonNode value) {

        switch (fieldType) {
            case "text", "textarea" -> {
                if (!value.isString()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " must be a string");
                }
                if (required && value.asString().isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " is required");
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " must be a number");
                }
            }
            case "email" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid email address");
                if (str != null && !EMAIL_PATTERN.matcher(str).matches()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " must be a valid email address");
                }
            }
            case "phone" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid phone number");
                if (str != null && !PHONE_PATTERN.matcher(str).matches()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            fieldId + " must be a valid phone number");
                }
            }
            case "date" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid date (YYYY-MM-DD)");
                if (str != null) {
                    try {
                        LocalDate.parse(str);
                    } catch (DateTimeParseException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldId + " must be a valid date (YYYY-MM-DD)");
                    }
                }
            }
            case "datetime" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid ISO datetime");
                if (str != null) {
                    if (!isValidIsoDateTime(str)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldId + " must be a valid ISO datetime");
                    }
                }
            }
            case "url" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid HTTP/HTTPS URL");
                if (str != null) {
                    try {
                        URI uri = URI.create(str);
                        String scheme = uri.getScheme();
                        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                                || uri.getHost() == null) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    fieldId + " must be a valid HTTP/HTTPS URL");
                        }
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldId + " must be a valid HTTP/HTTPS URL");
                    }
                }
            }
            case "checkbox" -> {
                JsonNode optionsNode = field.get("options");
                if (optionsNode != null && optionsNode.isArray() && !optionsNode.isEmpty()) {
                    validateArrayOptions(fieldId, required, optionsNode, value);
                } else {
                    if (!value.isBoolean()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldId + " must be a boolean");
                    }
                    if (required && !value.asBoolean()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                fieldId + " must be checked");
                    }
                }
            }
            case "select" -> {
                String str = requireNonBlankString(fieldId, required, value, "a valid option string");
                if (str != null) {
                    JsonNode optionsNode = field.get("options");
                    if (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty()) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Form field '" + fieldId + "' has no configured options");
                    }
                    Set<String> allowedOptions = FormService.extractOptionValues(fieldId, optionsNode);
                    if (!allowedOptions.contains(str)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid option for " + fieldId + ". Allowed values: " + allowedOptions);
                    }
                }
            }
            case "multiselect" -> {
                JsonNode optionsNode = field.get("options");
                if (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Form field '" + fieldId + "' has no configured options");
                }
                validateArrayOptions(fieldId, required, optionsNode, value);
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported field type in schema: " + fieldType);
        }
    }

    private String requireNonBlankString(
            String fieldId,
            boolean required,
            JsonNode value,
            String expectedDescription) {

        if (!value.isString()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldId + " must be " + expectedDescription);
        }

        String trimmed = value.asString().trim();
        if (trimmed.isBlank()) {
            if (required) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        fieldId + " is required");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldId + " must not be blank (omit or pass null if optional)");
        }
        return trimmed;
    }

    private void validateArrayOptions(
            String fieldId,
            boolean required,
            JsonNode optionsNode,
            JsonNode value) {

        if (!value.isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldId + " must be an array of selected options");
        }

        if (required && value.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldId + " is required");
        }

        Set<String> allowedOptions = FormService.extractOptionValues(fieldId, optionsNode);
        for (JsonNode item : value) {
            if (item == null || !item.isString() || item.asString().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All selected values for " + fieldId + " must be non-blank strings");
            }
            String optionValue = item.asString().trim();
            if (!allowedOptions.contains(optionValue)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid option '" + optionValue + "' for " + fieldId + ". Allowed values: " + allowedOptions);
            }
        }
    }

    private boolean isValidIsoDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
        }
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private AdminSubmissionResponse mapToAdminSubmissionResponse(Submission submission) {
        UUID reviewerId = submission.getAssignedReviewer() != null ? submission.getAssignedReviewer().getId() : null;
        String reviewerName = submission.getAssignedReviewer() != null ? submission.getAssignedReviewer().getName() : null;

        return new AdminSubmissionResponse(
                submission.getId(),
                submission.getAnswers(),
                submission.getStatus(),
                submission.getCategory(),
                reviewerId,
                reviewerName,
                submission.getAssignedAt(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                submission.getRejectionReason()
        );
    }

    private SubmissionDetailResponse mapToSubmissionDetailResponse(Submission submission) {
        UUID reviewerId = submission.getAssignedReviewer() != null ? submission.getAssignedReviewer().getId() : null;
        String reviewerName = submission.getAssignedReviewer() != null ? submission.getAssignedReviewer().getName() : null;
        UUID orgId = submission.getOrganization() != null ? submission.getOrganization().getId() : null;
        String orgName = submission.getOrganization() != null ? submission.getOrganization().getName() : null;

        return new SubmissionDetailResponse(
                submission.getId(),
                submission.getForm().getId(),
                submission.getForm().getTitle(),
                submission.getAnswers(),
                submission.getStatus(),
                submission.getCategory(),
                reviewerId,
                reviewerName,
                submission.getAssignedAt(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                submission.getRejectionReason(),
                submission.getDuplicateFlag(),
                orgId,
                orgName
        );
    }

    private ReviewerSubmissionResponse mapToReviewerSubmissionResponse(Submission submission) {
        return new ReviewerSubmissionResponse(
                submission.getId(),
                submission.getForm().getId(),
                submission.getForm().getTitle(),
                submission.getAnswers(),
                submission.getStatus(),
                submission.getCategory(),
                submission.getSubmittedAt(),
                submission.getAssignedAt(),
                submission.getReviewedAt(),
                submission.getRejectionReason()
        );
    }
}

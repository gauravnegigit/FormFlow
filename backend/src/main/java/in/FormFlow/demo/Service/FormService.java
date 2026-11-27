package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.common.PageResponse;
import in.FormFlow.demo.dto.form.CreateFormRequest;
import in.FormFlow.demo.dto.form.FormResponse;
import in.FormFlow.demo.dto.form.PublicFormResponse;
import in.FormFlow.demo.dto.form.UpdateFormRequest;
import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.Entity.Organization;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.FormStatus;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.Repository.FormRepository;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FormService {

    private final FormRepository formRepository;
    private final UserRepository userRepository;

    public FormService(
            FormRepository formRepository,
            UserRepository userRepository) {

        this.formRepository = formRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FormResponse createForm(CreateFormRequest request, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // 1. Get currently authenticated user
        UUID userId = UUID.fromString(authentication.getName());
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        requireAdmin(creator);

        // 2. Enforce Organization Multi-Tenancy: determine organization from DB
        Organization organization = creator.getOrganization();
        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User does not belong to any organization"
            );
        }

        // 3. Validate dynamic form schema
        validateSchema(request.schema());

        // 4. Create Form entity
        Form form = new Form();
        form.setTitle(request.title().trim());
        form.setDescription(request.description());
        form.setSchema(request.schema());
        form.setStatus(FormStatus.DRAFT);
        form.setCreatedBy(creator);
        form.setOrganization(organization);

        // 5. Generate unique public slug
        String slug = generateSlug(request.title());
        form.setPublicSlug(slug);

        // 6. Save form
        Form savedForm = formRepository.save(form);

        // 7. Return response DTO
        return mapToResponse(savedForm);
    }

    @Transactional(readOnly = true)
    public PageResponse<FormResponse> getMyForms(FormStatus status, Pageable pageable) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();
        Pageable safePageable = PageResponse.clamp(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Enforce organization isolation: return paginated forms in the user's organization
        Page<Form> forms = (status != null)
                ? formRepository.findAllByOrganization_IdAndStatus(orgId, status, safePageable)
                : formRepository.findAllByOrganization_Id(orgId, safePageable);

        return PageResponse.from(forms, this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public FormResponse getFormById(UUID formId) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();

        Form form = formRepository
                .findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        return mapToResponse(form);
    }

    @Transactional
    public FormResponse updateForm(UUID formId, UpdateFormRequest request) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();

        Form form = formRepository
                .findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        // Only draft forms can be edited
        if (form.getStatus() != FormStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft forms can be updated"
            );
        }

        validateSchema(request.schema());
        form.setTitle(request.title().trim());
        form.setDescription(request.description());
        form.setSchema(request.schema());

        Form updatedForm = formRepository.save(form);

        return mapToResponse(updatedForm);
    }

    @Transactional
    public void deleteForm(UUID formId) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();

        Form form = formRepository
                .findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        if (form.getStatus() != FormStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft forms can be deleted"
            );
        }

        formRepository.delete(form);
    }

    @Transactional
    public FormResponse publishForm(UUID formId) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();

        Form form = formRepository
                .findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        if (form.getStatus() != FormStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only draft forms can be published"
            );
        }

        form.setStatus(FormStatus.PUBLISHED);

        Form publishedForm = formRepository.save(form);

        return mapToResponse(publishedForm);
    }

    @Transactional
    public FormResponse closeForm(UUID formId) {

        User user = getAuthenticatedAdmin();
        UUID orgId = user.getOrganization().getId();

        Form form = formRepository
                .findByIdAndOrganization_Id(formId, orgId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found"));

        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only published forms can be closed"
            );
        }

        form.setStatus(FormStatus.CLOSED);

        Form closedForm = formRepository.save(form);

        return mapToResponse(closedForm);
    }

    @Transactional(readOnly = true)
    public PublicFormResponse getPublicForm(String slug) {

        Form form = formRepository
                .findByPublicSlugAndStatus(slug, FormStatus.PUBLISHED)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Form not found or is closed"));

        return new PublicFormResponse(
                form.getId(),
                form.getTitle(),
                form.getDescription(),
                form.getSchema(),
                form.getPublicSlug()
        );
    }

    private User getAuthenticatedAdmin() {
        User user = getAuthenticatedUser();
        requireAdmin(user);
        return user;
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only admins can manage forms"
            );
        }
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

    static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "text",
            "textarea",
            "number",
            "email",
            "phone",
            "date",
            "datetime",
            "url",
            "checkbox",
            "select",
            "multiselect"
    );

    private void validateSchema(JsonNode schema) {

        if (schema == null || !schema.isObject()) {
            throw new IllegalArgumentException(
                    "Schema must be a JSON object");
        }

        JsonNode fields = schema.get("fields");

        if (fields == null || !fields.isArray() || fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Schema must contain at least one field");
        }

        Set<String> seenFieldIds = new HashSet<>();

        for (JsonNode field : fields) {

            if (field == null || !field.isObject()
                    || !field.hasNonNull("id")
                    || !field.hasNonNull("label")
                    || !field.hasNonNull("type")
                    || !field.hasNonNull("required")) {

                throw new IllegalArgumentException(
                        "Each field must have id, label, type and required");
            }

            if (!field.get("id").isString()
                    || field.get("id").asString().isBlank()
                    || !field.get("label").isString()
                    || field.get("label").asString().isBlank()
                    || !field.get("type").isString()
                    || field.get("type").asString().isBlank()
                    || !field.get("required").isBoolean()) {

                throw new IllegalArgumentException(
                        "Invalid field property types");
            }

            String fieldId = field.get("id").asString().trim();
            if (!seenFieldIds.add(fieldId)) {
                throw new IllegalArgumentException(
                        "Duplicate field ID found: " + fieldId);
            }

            String fieldType = field.get("type").asString().trim().toLowerCase();
            if (!SUPPORTED_FIELD_TYPES.contains(fieldType)) {
                throw new IllegalArgumentException(
                        "Unsupported field type '" + field.get("type").asString() + "' for field: " + fieldId);
            }

            boolean requiresOptions = "select".equals(fieldType) || "multiselect".equals(fieldType);
            JsonNode optionsNode = field.get("options");

            if (requiresOptions && (optionsNode == null || !optionsNode.isArray() || optionsNode.isEmpty())) {
                throw new IllegalArgumentException(
                        "Field '" + fieldId + "' of type '" + fieldType + "' must define a non-empty options array");
            }

            if (optionsNode != null && !optionsNode.isNull()) {
                if (!optionsNode.isArray()) {
                    throw new IllegalArgumentException(
                            "Options for field '" + fieldId + "' must be an array");
                }
                extractOptionValues(fieldId, optionsNode);
            }
        }
    }

    static Set<String> extractOptionValues(String fieldId, JsonNode optionsNode) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode option : optionsNode) {
            String optionValue;
            if (option != null && option.isString()) {
                optionValue = option.asString().trim();
            } else if (option != null && option.isObject() && option.hasNonNull("value") && option.get("value").isString()) {
                optionValue = option.get("value").asString().trim();
                if (option.has("label") && !option.get("label").isNull()
                        && (!option.get("label").isString() || option.get("label").asString().isBlank())) {
                    throw new IllegalArgumentException(
                            "Option label in field '" + fieldId + "' must be a non-blank string");
                }
            } else {
                throw new IllegalArgumentException(
                        "Each option in field '" + fieldId + "' must be a non-blank string or an object with a non-blank 'value'");
            }

            if (optionValue.isBlank()) {
                throw new IllegalArgumentException(
                        "Option value in field '" + fieldId + "' cannot be blank");
            }

            if (!values.add(optionValue)) {
                throw new IllegalArgumentException(
                        "Duplicate option value '" + optionValue + "' in field '" + fieldId + "'");
            }
        }
        return values;
    }

    private String generateSlug(String title) {

        String normalized = title.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        if (normalized.isEmpty()) {
            normalized = "form";
        } else if (normalized.length() > 200) {
            normalized = normalized.substring(0, 200).replaceAll("-$", "");
        }

        return normalized + "-" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    private FormResponse mapToResponse(Form form) {

        UUID orgId = form.getOrganization() != null ? form.getOrganization().getId() : null;
        String orgName = form.getOrganization() != null ? form.getOrganization().getName() : null;

        return new FormResponse(
                form.getId(),
                form.getTitle(),
                form.getDescription(),
                form.getSchema(),
                form.getStatus(),
                form.getPublicSlug(),
                form.getCreatedAt(),
                form.getUpdatedAt(),
                orgId,
                orgName
        );
    }
}

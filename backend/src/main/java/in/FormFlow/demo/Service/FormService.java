
package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.form.CreateFormRequest;
import in.FormFlow.demo.dto.form.FormResponse;
import in.FormFlow.demo.dto.form.PublicFormResponse;
import in.FormFlow.demo.dto.form.UpdateFormRequest;
import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.FormStatus;
import in.FormFlow.demo.Repository.FormRepository;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.exception.ResourceNotFoundException;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
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

        // 1. Get currently authenticated user ID from injected Authentication
        UUID userId = UUID.fromString(authentication.getName());

        // 2. Find the creator in the database
        User creator = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        // 3. Validate dynamic form schema
        validateSchema(request.schema());

        // 4. Create Form entity
        Form form = new Form();

        form.setTitle(request.title().trim());
        form.setDescription(request.description());
        form.setSchema(request.schema());
        form.setStatus(FormStatus.DRAFT);
        form.setCreatedBy(creator);

        // 5. Generate unique public slug
        String slug = generateSlug(request.title());
        form.setPublicSlug(slug);

        // 6. Save form
        Form savedForm = formRepository.save(form);

        // 7. Return response DTO
        return mapToResponse(savedForm);
    }

    private void validateSchema(JsonNode schema) {

        if (!schema.isObject()) {
            throw new IllegalArgumentException(
                    "Schema must be a JSON object");
        }

        JsonNode fields = schema.get("fields");

        if (fields == null || !fields.isArray() || fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Schema must contain at least one field");
        }

        for (JsonNode field : fields) {

            if (!field.isObject()
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
        }
    }

    private String generateSlug(String title) {

        String normalized = title.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        return normalized + "-" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    private FormResponse mapToResponse(Form form) {

        return new FormResponse(
                form.getId(),
                form.getTitle(),
                form.getDescription(),
                form.getSchema(),
                form.getStatus(),
                form.getPublicSlug(),
                form.getCreatedAt(),
                form.getUpdatedAt()
        );
    }


@Transactional(readOnly = true)
public List<FormResponse> getMyForms() {

    // 1. Get currently logged-in user
    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    // 2. Fetch only forms created by this user
    List<Form> forms =
            formRepository.findAllByCreatedBy_Id(userId);

    // 3. Convert entities into response DTOs
    return forms.stream()
            .map(this::mapToResponse)
            .toList();
}

@Transactional(readOnly = true)
public FormResponse getFormById(UUID formId) {

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    Form form = formRepository
            .findByIdAndCreatedBy_Id(formId, userId)
            .orElseThrow(() ->
                    new ResourceNotFoundException("Form not found"));

    return mapToResponse(form);
}

@Transactional
public FormResponse updateForm(UUID formId, UpdateFormRequest request) {

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    Form form = formRepository
            .findByIdAndCreatedBy_Id(formId, userId)
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

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    Form form = formRepository
            .findByIdAndCreatedBy_Id(formId, userId)
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

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    Form form = formRepository
            .findByIdAndCreatedBy_Id(formId, userId)
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

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    UUID userId = UUID.fromString(authentication.getName());

    Form form = formRepository
            .findByIdAndCreatedBy_Id(formId, userId)
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
                    new ResourceNotFoundException("Form not found"));

    return new PublicFormResponse(
            form.getId(),
            form.getTitle(),
            form.getDescription(),
            form.getSchema(),
            form.getPublicSlug()
    );
}

}

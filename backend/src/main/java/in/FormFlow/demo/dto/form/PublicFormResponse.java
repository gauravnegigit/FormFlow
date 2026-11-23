package in.FormFlow.demo.dto.form;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PublicFormResponse(
        UUID id,
        String title,
        String description,
        JsonNode schema,
        String publicSlug
) {}
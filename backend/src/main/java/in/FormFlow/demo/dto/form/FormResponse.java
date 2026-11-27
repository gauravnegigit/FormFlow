package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.FormStatus;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record FormResponse(

        UUID id,

        String title,

        String description,

        JsonNode schema,

        FormStatus status,

        String publicSlug,

        LocalDateTime createdAt,

        LocalDateTime updatedAt,

        UUID organizationId,

        String organizationName

) {}

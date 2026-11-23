
package in.FormFlow.demo.dto.form;

import tools.jackson.databind.JsonNode;

import in.FormFlow.demo.enums.FormStatus;

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

        LocalDateTime updatedAt

) {}

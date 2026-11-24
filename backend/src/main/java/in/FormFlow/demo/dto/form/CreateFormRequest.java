
package in.FormFlow.demo.dto.form;

import tools.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFormRequest(

        @NotBlank(message = "Form title is required")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        @Size(max = 2000, message = "Description is too long")
        String description,

        @NotNull(message = "Form schema is required")
        JsonNode schema

) {}

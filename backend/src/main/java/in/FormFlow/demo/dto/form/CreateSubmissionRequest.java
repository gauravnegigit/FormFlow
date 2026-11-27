package in.FormFlow.demo.dto.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CreateSubmissionRequest(
        @NotNull(message = "Answers are required")
        JsonNode answers,

        @Size(max = 100, message = "Category cannot exceed 100 characters")
        String category
) {}

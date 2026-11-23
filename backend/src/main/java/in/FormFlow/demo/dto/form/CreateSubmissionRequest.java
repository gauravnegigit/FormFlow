package in.FormFlow.demo.dto.form;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record CreateSubmissionRequest(
        @NotNull(message = "Answers are required")
        JsonNode answers
) {}
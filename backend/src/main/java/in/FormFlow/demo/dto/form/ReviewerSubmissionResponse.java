package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.SubmissionStatus;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewerSubmissionResponse(
        UUID id,
        UUID formId,
        String formTitle,
        JsonNode answers,
        SubmissionStatus status,
        String category,
        LocalDateTime submittedAt,
        LocalDateTime assignedAt,
        LocalDateTime reviewedAt,
        String rejectionReason
) {}

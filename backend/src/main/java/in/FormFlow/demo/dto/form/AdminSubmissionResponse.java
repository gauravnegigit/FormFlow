package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.SubmissionStatus;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminSubmissionResponse(
        UUID id,
        JsonNode answers,
        SubmissionStatus status,
        String category,
        UUID assignedReviewerId,
        String assignedReviewerName,
        LocalDateTime assignedAt,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectionReason
) {}

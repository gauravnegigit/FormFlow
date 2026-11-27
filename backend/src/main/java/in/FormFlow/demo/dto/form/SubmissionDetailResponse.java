package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.SubmissionStatus;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubmissionDetailResponse(
        UUID id,
        UUID formId,
        String formTitle,
        JsonNode answers,
        SubmissionStatus status,
        String category,
        UUID assignedReviewerId,
        String assignedReviewerName,
        LocalDateTime assignedAt,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectionReason,
        Boolean duplicateFlag,
        UUID organizationId,
        String organizationName
) {}

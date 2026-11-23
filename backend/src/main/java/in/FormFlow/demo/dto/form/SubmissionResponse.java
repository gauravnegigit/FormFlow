package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        SubmissionStatus status,
        String trackingToken,
        LocalDateTime submittedAt
) {}
package in.FormFlow.demo.dto.form;

import in.FormFlow.demo.enums.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicSubmissionTrackResponse(
        UUID id,
        String formTitle,
        SubmissionStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String rejectionReason
) {}

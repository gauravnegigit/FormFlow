package in.FormFlow.demo.dto.form;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignReviewerRequest(
        @NotNull(message = "Reviewer ID is required")
        UUID reviewerId
) {}
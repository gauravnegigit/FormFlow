package in.FormFlow.demo.dto.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateAdminInvitationRequest(

        @Min(value = 1, message = "Expiry must be at least 1 hour")
        @Max(value = 168, message = "Expiry cannot exceed 168 hours")
        Integer expiresInHours
) {}

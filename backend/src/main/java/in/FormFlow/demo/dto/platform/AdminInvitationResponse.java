package in.FormFlow.demo.dto.platform;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminInvitationResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String invitationToken,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {}

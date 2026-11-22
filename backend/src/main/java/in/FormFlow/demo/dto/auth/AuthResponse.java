
package in.FormFlow.demo.dto.auth;

import in.FormFlow.demo.Entity.enums.UserRole;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UUID userId,
        String name,
        String email,
        UserRole role
) {}

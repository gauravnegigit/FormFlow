
package in.FormFlow.demo.dto.auth;

import in.FormFlow.demo.enums.UserRole;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String name,
    String email,
    UserRole role
) {}

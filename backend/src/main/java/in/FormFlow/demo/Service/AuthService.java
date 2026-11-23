
package in.FormFlow.demo.Service;

import in.FormFlow.demo.dto.auth.RegisterRequest;
import in.FormFlow.demo.dto.auth.UserResponse;
import in.FormFlow.demo.dto.auth.LoginRequest;
import in.FormFlow.demo.dto.auth.AuthResponse;
import in.FormFlow.demo.exception.DuplicateEmailException;
import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.UserRole;
import in.FormFlow.demo.Repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder , 
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();

        // 1. Check if email already exists
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException("Email already registered");
        }

        // 2. Create a new User entity
        User user = new User();

        user.setName(request.name().trim());
        user.setEmail(email);

        // 3. Hash the password
        user.setPassword(passwordEncoder.encode(request.password()));

        // 4. Assign the default role
        user.setRole(UserRole.ADMIN);

        // 5. Save user in database
        User savedUser = userRepository.save(user);

        // 6. Convert Entity into Response DTO
        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }


public AuthResponse login(LoginRequest request) {

    String email = request.email().trim().toLowerCase();

    // 1. Find user by email
    User user = userRepository.findByEmail(email)
            .orElseThrow(() ->
                    new BadCredentialsException("Invalid email or password")
            );

    // 2. Verify password
    if (!passwordEncoder.matches(
            request.password(),
            user.getPassword())) {

        throw new BadCredentialsException("Invalid email or password");
    }

    // 3. Generate JWT
    String token = jwtService.generateToken(
            user.getId(),
            user.getEmail(),
            user.getRole().name()
    );

    // 4. Return authentication response
    return new AuthResponse(
            token,
            "Bearer",
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole()
    );
}

}

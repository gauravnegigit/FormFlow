
package in.FormFlow.demo.Controller;

import in.FormFlow.demo.dto.auth.AuthResponse;
import in.FormFlow.demo.dto.auth.LoginRequest;
import in.FormFlow.demo.dto.auth.RegisterRequest;
import in.FormFlow.demo.dto.auth.UserResponse;
import in.FormFlow.demo.Service.AuthService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;  

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test-public")
    public String publicTest() {
        return "Public endpoint working";
    }

    @GetMapping("/test-protected")
    public String protectedTest() {
        return "You are authenticated!";
    }
}

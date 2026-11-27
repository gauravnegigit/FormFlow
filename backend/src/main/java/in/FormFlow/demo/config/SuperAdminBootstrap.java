package in.FormFlow.demo.config;

import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String superAdminEmail;
    private final String superAdminPassword;
    private final String superAdminName;

    public SuperAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.super-admin.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.super-admin.email:}") String superAdminEmail,
            @Value("${app.bootstrap.super-admin.password:}") String superAdminPassword,
            @Value("${app.bootstrap.super-admin.name:Platform Owner}") String superAdminName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.superAdminEmail = superAdminEmail != null ? superAdminEmail.trim().toLowerCase() : "";
        this.superAdminPassword = superAdminPassword != null ? superAdminPassword : "";
        this.superAdminName = (superAdminName != null && !superAdminName.trim().isBlank())
                ? superAdminName.trim()
                : "Platform Owner";
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        boolean hasEmail = !superAdminEmail.isBlank();
        boolean hasPassword = !superAdminPassword.isBlank();

        if (hasEmail != hasPassword) {
            throw new IllegalStateException(
                    "Incomplete SUPER_ADMIN bootstrap configuration: both SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD must be provided together."
            );
        }

        if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            log.debug("SUPER_ADMIN account already exists; skipping bootstrap.");
            return;
        }

        if (!hasEmail) {
            throw new IllegalStateException(
                    "Missing required SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD environment variables to initialize the initial platform owner."
            );
        }

        if (!superAdminEmail.contains("@") || superAdminEmail.length() > 255) {
            throw new IllegalStateException("Invalid SUPER_ADMIN_EMAIL format.");
        }

        if (superAdminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Weak SUPER_ADMIN_PASSWORD configuration: password must be at least " + MIN_PASSWORD_LENGTH + " characters."
            );
        }

        if (userRepository.existsByEmail(superAdminEmail)) {
            throw new IllegalStateException(
                    "Configured SUPER_ADMIN_EMAIL is already registered to a non-SUPER_ADMIN account."
            );
        }

        User superAdmin = new User();
        superAdmin.setName(superAdminName);
        superAdmin.setEmail(superAdminEmail);
        superAdmin.setPassword(passwordEncoder.encode(superAdminPassword));
        superAdmin.setRole(UserRole.SUPER_ADMIN);
        superAdmin.setOrganization(null);

        User saved = userRepository.save(superAdmin);
        log.info("Bootstrapped initial SUPER_ADMIN account id={}", saved.getId());
    }
}

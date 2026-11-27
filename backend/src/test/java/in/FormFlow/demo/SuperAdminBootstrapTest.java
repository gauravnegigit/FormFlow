package in.FormFlow.demo;

import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.Repository.UserRepository;
import in.FormFlow.demo.config.SuperAdminBootstrap;
import in.FormFlow.demo.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SuperAdminBootstrapTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void missingEnvVarsThrowsWhenNoSuperAdminExists() {
        userRepository.deleteAll();

        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "",
                "",
                "Platform Owner"
        );

        assertThrows(IllegalStateException.class,
                () -> bootstrap.run(new DefaultApplicationArguments()));
    }

    @Test
    void bootstrapIsIdempotentAndHashesPassword() {
        User superAdmin = userRepository.findByEmail("owner@formflow.test").orElseThrow();
        assertEquals(UserRole.SUPER_ADMIN, superAdmin.getRole());
        assertNull(superAdmin.getOrganization());
        assertNotEquals("OwnerPass123!", superAdmin.getPassword());
        assertTrue(passwordEncoder.matches("OwnerPass123!", superAdmin.getPassword()));

        // Re-running bootstrap must not duplicate accounts
        SuperAdminBootstrap rerun = new SuperAdminBootstrap(
                userRepository,
                passwordEncoder,
                true,
                "owner@formflow.test",
                "OwnerPass123!",
                "Test Platform Owner"
        );
        rerun.run(new DefaultApplicationArguments());

        assertEquals(1, userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN)
                .count());
    }
}

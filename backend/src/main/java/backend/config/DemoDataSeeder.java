package backend.config;

import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.demo-password:}")
    private String demoPassword;

    @Override
    public void run(String... args) {
        if (demoPassword == null || demoPassword.length() < 8) {
            log.warn("Demo users not created: DEMO_PASSWORD must be at least 8 characters.");
            return;
        }
        seed("admin", "Demo Admin", Role.ADMIN);
        seed("reception", "Demo Receptionist", Role.RECEPTIONIST);
        seed("doctor", "Dr. Demo Doctor", Role.DOCTOR);
        seed("nurse", "Demo Nurse", Role.NURSE);
        seed("pharmacist", "Demo Pharmacist", Role.PHARMACIST);
        seed("cashier", "Demo Cashier", Role.CASHIER);
        log.info("Demo users are ready.");
    }

    private void seed(String username, String fullName, Role role) {
        if (users.existsByUsername(username)) return;
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(demoPassword));
        users.save(u);
    }
}
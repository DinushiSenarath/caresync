package backend.auth;

import backend.auth.AuthDtos.*;
import backend.common.ApiException;
import backend.security.TokenService;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;

    public LoginResponse login(LoginRequest req) {
        User u = users.findByUsername(req.username()).orElse(null);
        if (u == null || !u.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked. Try again in a few minutes.");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= MAX_ATTEMPTS) {
                u.setLockedUntil(now.plus(LOCK_TIME));
                u.setFailedAttempts(0);
            }
            users.save(u);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLogin(now);
        users.save(u);
        return new LoginResponse(tokens.issue(u), "Bearer", tokens.expirySeconds(),
                u.getUsername(), u.getFullName(), u.getRole().name());
    }

    public MeResponse me(String username) {
        User u = find(username);
        return new MeResponse(u.getUsername(), u.getFullName(), u.getEmail(), u.getRole().name());
    }

    public void changePassword(String username, ChangePasswordRequest req) {
        User u = find(username);
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
    }

    private User find(String username) {
        return users.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }
}
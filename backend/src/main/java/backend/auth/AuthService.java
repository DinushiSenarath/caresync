package backend.auth;

import backend.audit.AuditService;
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
    private final AuditService audit;

    public LoginResponse login(LoginRequest req) {
        String username = req.username().trim().toLowerCase();
        User u = users.findByUsername(username).orElse(null);
        if (u == null || !u.isActive()) {
            audit.log(null, "LOGIN_FAILED", "USER", null, "unknown or inactive: " + shorten(username));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            audit.log(u.getId(), "LOGIN_BLOCKED", "USER", u.getId(), "account locked");
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked. Try again in a few minutes.");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            boolean nowLocked = u.getFailedAttempts() >= MAX_ATTEMPTS;
            if (nowLocked) {
                u.setLockedUntil(now.plus(LOCK_TIME));
                u.setFailedAttempts(0);
            }
            users.save(u);
            audit.log(u.getId(), nowLocked ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", "USER", u.getId(), "wrong password");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        u.setLastLogin(now);
        users.save(u);
        audit.log(u.getId(), "LOGIN_SUCCESS", "USER", u.getId(), null);
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
        audit.log(u.getId(), "PASSWORD_CHANGED", "USER", u.getId(), null);
    }

    private User find(String username) {
        return users.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }

    private String shorten(String s) {
        return s.length() > 50 ? s.substring(0, 50) : s;
    }
}
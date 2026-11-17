package backend.user;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.user.UserDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest r, Long actorId) {
        requireStaffRole(r.role());
        String username = r.username().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setEmail(r.email());
        u.setRole(r.role());
        users.save(u);
        audit.log(actorId, "USER_CREATED", "USER", u.getId(), "username=" + username + ", role=" + r.role());
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest r, Long actorId) {
        requireStaffRole(r.role());
        User u = get(id);
        if (u.getRole() == Role.PATIENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Patient accounts are managed through patient records");
        }
        u.setFullName(r.fullName().trim());
        u.setEmail(r.email());
        u.setRole(r.role());
        audit.log(actorId, "USER_UPDATED", "USER", id, "role=" + r.role());
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active, Long actorId) {
        if (!active && id.equals(actorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }
        User u = get(id);
        u.setActive(active);
        audit.log(actorId, active ? "USER_REACTIVATED" : "USER_DEACTIVATED", "USER", id, null);
        return UserResponse.from(u);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, Long actorId) {
        User u = get(id);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        audit.log(actorId, "PASSWORD_RESET", "USER", id, null);
    }

    private User get(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireStaffRole(Role role) {
        if (role == Role.PATIENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Patient accounts are created through the patient activation process");
        }
    }
}
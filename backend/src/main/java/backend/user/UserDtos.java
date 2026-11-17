package backend.user;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "letters, digits, dot, dash and underscore only")
            String username,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @NotNull Role role) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 120) String email,
            @NotNull Role role) {}

    public record SetActiveRequest(@NotNull Boolean active) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {}

    public record UserResponse(Long id, String username, String fullName, String email,
                               Role role, boolean active, Instant lastLogin) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                    u.getRole(), u.isActive(), u.getLastLogin());
        }
    }
}
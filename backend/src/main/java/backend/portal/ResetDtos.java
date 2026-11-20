package backend.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ResetDtos {
    private ResetDtos() {}

    public record IssueResetRequest(
            @NotBlank(message = "state how the identity was checked") @Size(max = 255) String verification) {}

    public record PatientResetRequest(
            @NotBlank @Size(max = 20) String patientNo,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {}

    public record ResetResponse(String message) {}
}
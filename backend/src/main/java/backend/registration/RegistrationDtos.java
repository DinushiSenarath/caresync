package backend.registration;

import backend.patient.Gender;
import backend.portal.ActivationService.IssuedCode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

public final class RegistrationDtos {
    private RegistrationDtos() {}

    private static final String PHONE = "^(\\+94|0)[0-9]{9}$";
    private static final String PHONE_MSG = "use a Sri Lankan number such as 0771234567 or +94771234567";

    public record PublicRegistrationRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull Gender gender,
            @Pattern(regexp = "^$|^([0-9]{9}[VvXx]|[0-9]{12})$", message = "invalid NIC format") String nic,
            @NotBlank @Pattern(regexp = PHONE, message = PHONE_MSG) String phone,
            @Size(max = 255) String address,
            @NotNull @AssertTrue(message = "you must agree before submitting") Boolean consent,
            @Size(max = 100) String website) {}

    public record PublicRegistrationResponse(String message) {}

    public record RegistrationResponse(Long id, String fullName, LocalDate dateOfBirth, Gender gender, String nic,
                                       String phone, String address, RegistrationStatus status, Long patientId,
                                       String patientNo, String decisionNote, String decidedByName,
                                       Instant createdAt, Instant decidedAt) {}

    public record ApproveRequest(
            Long existingPatientId,
            Boolean issueActivationCode,
            @NotBlank @Size(max = 255) String note) {}

    public record RejectRequest(@NotBlank @Size(max = 255) String note) {}

    public record ApprovalResponse(RegistrationResponse request, Long patientId, String patientNo,
                                   boolean linkedToExistingPatient, IssuedCode activationCode, String message) {}
}
package backend.patient;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public final class PatientDtos {
    private PatientDtos() {}

    private static final String PHONE = "^(\\+94|0)[0-9]{9}$";
    private static final String PHONE_MSG = "use a Sri Lankan number such as 0771234567 or +94771234567";

    public record PatientRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull Gender gender,
            @Pattern(regexp = "^$|^([0-9]{9}[VvXx]|[0-9]{12})$", message = "invalid NIC format") String nic,
            @NotBlank @Pattern(regexp = PHONE, message = PHONE_MSG) String phone,
            @Size(max = 255) String address,
            @Size(max = 120) String emergencyContactName,
            @Pattern(regexp = "^$|" + PHONE, message = PHONE_MSG) String emergencyContactPhone,
            @Size(max = 2000) String allergies) {}

    public record PatientResponse(Long id, String patientNo, String fullName, LocalDate dateOfBirth, Gender gender,
                                  String nic, String phone, String address, String emergencyContactName,
                                  String emergencyContactPhone, String allergies, boolean active) {}

    public record DuplicateMatch(Long id, String patientNo, String fullName, LocalDate dateOfBirth, String phone) {}
}
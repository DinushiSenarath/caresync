package backend.portal;

import backend.appointment.AppointmentDtos.AppointmentResponse;
import backend.patient.Gender;
import backend.prescription.PrescriptionDtos.PrescriptionResponse;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PortalDtos {
    private PortalDtos() {}

    private static final String PHONE = "^(\\+94|0)[0-9]{9}$";
    private static final String PHONE_MSG = "use a Sri Lankan number such as 0771234567 or +94771234567";

    public record ActivateRequest(
            @NotBlank @Size(max = 20) String patientNo,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "letters, digits, dot, dash and underscore only")
            String username,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password) {}

    public record ActivationResponse(String message) {}

    public record ProfileResponse(Long id, String patientNo, String fullName, LocalDate dateOfBirth, Gender gender,
                                  String nic, String phone, String address, String emergencyContactName,
                                  String emergencyContactPhone, String allergies) {}

    public record ProfileUpdateRequest(
            @NotBlank @Pattern(regexp = PHONE, message = PHONE_MSG) String phone,
            @Size(max = 255) String address,
            @Size(max = 120) String emergencyContactName,
            @Pattern(regexp = "^$|" + PHONE, message = PHONE_MSG) String emergencyContactPhone) {}

    public record DoctorOption(Long id, String name, String department, String specialty, String workDays) {}

    public record AppointmentRequestBody(
            @NotNull Long doctorId,
            @NotNull LocalDate date,
            @NotNull LocalTime time,
            @Size(max = 255) String reason) {}

    public record CancelAppointmentRequest(@Size(max = 255) String reason) {}

    public record HomeResponse(String fullName, AppointmentResponse nextAppointment,
                               List<PrescriptionResponse> recentPrescriptions, BigDecimal outstandingBalance) {}
}
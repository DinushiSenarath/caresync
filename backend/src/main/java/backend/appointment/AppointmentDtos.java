package backend.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public final class AppointmentDtos {
    private AppointmentDtos() {}

    public record BookAppointmentRequest(
            @NotNull Long patientId,
            @NotNull Long doctorId,
            @NotNull LocalDate date,
            @NotNull LocalTime time,
            @Size(max = 255) String reason) {}

    public record RescheduleRequest(
            @NotNull Long doctorId,
            @NotNull LocalDate date,
            @NotNull LocalTime time) {}

    public record StatusChangeRequest(
            @NotNull AppointmentStatus status,
            @Size(max = 255) String reason) {}

    public record SlotResponse(LocalTime time, boolean available) {}

    public record AppointmentResponse(Long id, Long patientId, String patientNo, String patientName,
                                      Long doctorId, String doctorName, String departmentName,
                                      LocalDate date, LocalTime startTime, AppointmentStatus status,
                                      String reason, String cancelReason) {
        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(
                    a.getId(),
                    a.getPatient().getId(), a.getPatient().getPatientNo(), a.getPatient().getFullName(),
                    a.getDoctor().getId(), a.getDoctor().getUser().getFullName(),
                    a.getDoctor().getDepartment().getName(),
                    a.getAppointmentDate(), a.getStartTime(), a.getStatus(),
                    a.getReason(), a.getCancelReason());
        }
    }
}
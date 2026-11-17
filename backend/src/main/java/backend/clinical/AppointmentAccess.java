package backend.clinical;

import backend.appointment.Appointment;
import backend.appointment.AppointmentRepository;
import backend.common.ApiException;
import backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class AppointmentAccess {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    private final AppointmentRepository appointments;

    /** Loads the appointment and enforces ownership: doctors only their own, nurses only today's. */
    public Appointment load(Long id, Long actorId, Role role) {
        Appointment a = appointments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (role == Role.DOCTOR && !a.getDoctor().getUser().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This appointment belongs to another doctor");
        }
        if (role == Role.NURSE && !a.getAppointmentDate().equals(LocalDate.now(ZONE))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nurses can only access today's appointments");
        }
        return a;
    }
}
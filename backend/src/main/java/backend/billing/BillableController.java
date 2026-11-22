package backend.billing;

import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillableController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    public record BillableAppointment(Long appointmentId, LocalDate date, LocalTime startTime, String patientNo,
                                      String patientName, String doctorName, AppointmentStatus status) {}

    private final AppointmentRepository appointments;
    private final InvoiceRepository invoices;

    /** Visits from the last few days that have started but have no active invoice yet. */
    @GetMapping("/billable-appointments")
    @PreAuthorize("hasRole('CASHIER')")
    @Transactional(readOnly = true)
    public List<BillableAppointment> billable(@RequestParam(defaultValue = "7") int days) {
        LocalDate today = LocalDate.now(ZONE);
        int safe = Math.min(Math.max(days, 0), 60);
        return appointments.search(today.minusDays(safe), today,
                        List.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED),
                        true, 0L, true, 0L, PageRequest.of(0, 100))
                .stream()
                .filter(a -> !invoices.existsByAppointmentIdAndStatusNot(a.getId(), InvoiceStatus.VOIDED))
                .map(a -> new BillableAppointment(a.getId(), a.getAppointmentDate(), a.getStartTime(),
                        a.getPatient().getPatientNo(), a.getPatient().getFullName(),
                        a.getDoctor().getUser().getFullName(), a.getStatus()))
                .toList();
    }
}
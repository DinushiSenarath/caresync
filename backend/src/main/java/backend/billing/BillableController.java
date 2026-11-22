package backend.billing;

import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentStatus;
import backend.lab.LabRequestRepository;
import backend.lab.LabStatus;
import backend.ward.AdmissionRepository;
import backend.ward.AdmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillableController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

    public record BillableAppointment(Long appointmentId, LocalDate date, LocalTime startTime, String patientNo,
                                      String patientName, String doctorName, AppointmentStatus status,
                                      long pendingLabRequests) {}

    public record BillableAdmission(Long admissionId, String admissionNo, String patientNo, String patientName,
                                    String doctorName, Instant admittedAt, Instant dischargedAt) {}

    private final AppointmentRepository appointments;
    private final InvoiceRepository invoices;
    private final LabRequestRepository labRequests;
    private final AdmissionRepository admissions;

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
                        a.getDoctor().getUser().getFullName(), a.getStatus(),
                        labRequests.countByAppointmentIdAndStatusIn(a.getId(),
                                List.of(LabStatus.REQUESTED, LabStatus.COLLECTED))))
                .toList();
    }

    /** Patients discharged recently whose stay has not been invoiced yet. */
    @GetMapping("/billable-admissions")
    @PreAuthorize("hasRole('CASHIER')")
    @Transactional(readOnly = true)
    public List<BillableAdmission> billableAdmissions(@RequestParam(defaultValue = "30") int days) {
        int safe = Math.min(Math.max(days, 1), 120);
        Instant since = Instant.now().minus(safe, ChronoUnit.DAYS);
        return admissions.findByStatusAndDischargedAtAfterOrderByDischargedAtDesc(AdmissionStatus.DISCHARGED, since)
                .stream()
                .filter(a -> !invoices.existsByAdmissionIdAndStatusNot(a.getId(), InvoiceStatus.VOIDED))
                .map(a -> new BillableAdmission(a.getId(), a.getAdmissionNo(), a.getPatient().getPatientNo(),
                        a.getPatient().getFullName(), a.getResponsibleDoctor().getUser().getFullName(),
                        a.getAdmittedAt(), a.getDischargedAt()))
                .toList();
    }
}
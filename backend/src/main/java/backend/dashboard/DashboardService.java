package backend.dashboard;

import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentStatus;
import backend.billing.AdjustmentStatus;
import backend.billing.InvoiceAdjustmentRepository;
import backend.billing.InvoiceRepository;
import backend.billing.InvoiceStatus;
import backend.billing.PaymentRepository;
import backend.clinical.VitalSignRepository;
import backend.common.ApiException;
import backend.dashboard.DashboardDtos.DashboardResponse;
import backend.dashboard.DashboardDtos.QueueItem;
import backend.doctor.Doctor;
import backend.doctor.DoctorRepository;
import backend.patient.PatientRepository;
import backend.pharmacy.PharmacyDtos.AlertsResponse;
import backend.pharmacy.PharmacyService;
import backend.prescription.PrescriptionRepository;
import backend.prescription.PrescriptionStatus;
import backend.registration.RegistrationRequestRepository;
import backend.registration.RegistrationStatus;
import backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.lab.LabPriority;
import backend.lab.LabRequestRepository;
import backend.lab.LabStatus;
import backend.ward.AdmissionRepository;
import backend.ward.AdmissionStatus;
import backend.ward.BedRepository;
import backend.ward.BedStayRepository;
import backend.hr.LeaveRequestRepository;
import backend.hr.LeaveStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final List<AppointmentStatus> ACTIVE_TODAY =
            List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS);

    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final VitalSignRepository vitals;
    private final PrescriptionRepository prescriptions;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final InvoiceAdjustmentRepository adjustments;
    private final RegistrationRequestRepository registrations;
    private final PharmacyService pharmacy;
    private final LabRequestRepository labRequests;
    private final AdmissionRepository admissions;
    private final BedRepository beds;
    private final BedStayRepository bedStays;
    private final LeaveRequestRepository leaveRequests;

    @Transactional(readOnly = true)
    public DashboardResponse summary(Long userId, Role role) {
        LocalDate today = LocalDate.now(ZONE);
        Map<String, Object> c = new LinkedHashMap<>();
        List<QueueItem> queue = List.of();

        switch (role) {
            case ADMIN -> {
                c.put("activePatients", patients.countByActiveTrue());
                c.put("activeDoctors", doctors.countByActiveTrue());
                c.put("appointmentsToday", appointments.countByAppointmentDate(today));
                c.put("appointmentsTodayByStatus", todayByStatus(today));
                c.put("pendingAppointmentRequests", appointments.countByStatus(AppointmentStatus.REQUESTED));
                c.put("pendingRegistrations", registrations.countByStatus(RegistrationStatus.PENDING));
                c.put("pendingAdjustments", adjustments.countByStatus(AdjustmentStatus.PENDING));
                c.put("labRequestsPending", labRequests.countByStatusIn(List.of(LabStatus.REQUESTED, LabStatus.COLLECTED)));
                c.put("inpatientsNow", admissions.countByStatus(AdmissionStatus.ADMITTED));
                c.put("pendingLeaveRequests", leaveRequests.countByStatus(LeaveStatus.PENDING));
                c.put("freeBeds", Math.max(0L, beds.countUsable() - bedStays.countByToAtIsNull()));
                c.put("revenueToday", collectedToday(today));
                addPharmacyAlerts(c);
            }
            case RECEPTIONIST -> {
                c.put("appointmentsToday", appointments.countByAppointmentDate(today));
                c.put("scheduledToday", appointments.countByAppointmentDateAndStatus(today, AppointmentStatus.SCHEDULED));
                c.put("checkedInToday", appointments.countByAppointmentDateAndStatus(today, AppointmentStatus.CHECKED_IN));
                c.put("pendingAppointmentRequests", appointments.countByStatus(AppointmentStatus.REQUESTED));
                c.put("pendingRegistrations", registrations.countByStatus(RegistrationStatus.PENDING));
                queue = queueFor(today, ACTIVE_TODAY, true, 0L, false);
            }
            case DOCTOR -> {
                Doctor me = doctors.findByUserId(userId).orElse(null);
                if (me == null) {
                    c.put("note", "No doctor profile is linked to this account");
                } else {
                    c.put("appointmentsToday", appointments.countByDoctorIdAndAppointmentDate(me.getId(), today));
                    c.put("waiting", appointments.countByDoctorIdAndAppointmentDateAndStatus(
                            me.getId(), today, AppointmentStatus.CHECKED_IN));
                    c.put("inProgress", appointments.countByDoctorIdAndAppointmentDateAndStatus(
                            me.getId(), today, AppointmentStatus.IN_PROGRESS));
                    c.put("completedToday", appointments.countByDoctorIdAndAppointmentDateAndStatus(
                            me.getId(), today, AppointmentStatus.COMPLETED));
                    queue = queueFor(today, ACTIVE_TODAY, false, me.getId(), true);
                }
            }
            case NURSE -> {
                List<QueueItem> checkedIn = queueFor(today, List.of(AppointmentStatus.CHECKED_IN), true, 0L, true);
                c.put("checkedInToday", checkedIn.size());
                c.put("waitingForVitals", checkedIn.stream().filter(q -> !Boolean.TRUE.equals(q.vitalsRecorded())).count());
                c.put("inProgressToday", appointments.countByAppointmentDateAndStatus(today, AppointmentStatus.IN_PROGRESS));
                queue = checkedIn;
            }
            case PHARMACIST -> {
                c.put("pendingPrescriptions", prescriptions.countByStatusIn(
                        List.of(PrescriptionStatus.ISSUED, PrescriptionStatus.PARTIALLY_DISPENSED)));
                addPharmacyAlerts(c);
            }
            case CASHIER -> {
                c.put("unpaidInvoices", invoices.countByStatus(InvoiceStatus.UNPAID));
                c.put("partiallyPaidInvoices", invoices.countByStatus(InvoiceStatus.PARTIALLY_PAID));
                c.put("outstandingAmount", nz(invoices.outstanding(
                        List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID))));
                c.put("collectedToday", collectedToday(today));
                c.put("adjustmentsWaiting", adjustments.countByStatus(AdjustmentStatus.PENDING));
            }

            case LAB_TECH -> {
                Instant dayStart = today.atStartOfDay(ZONE).toInstant();
                Instant dayEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
                c.put("waitingForSample", labRequests.countByStatus(LabStatus.REQUESTED));
                c.put("urgentWaitingForSample", labRequests.countByStatusAndPriority(LabStatus.REQUESTED, LabPriority.URGENT));
                c.put("awaitingResults", labRequests.countByStatus(LabStatus.COLLECTED));
                c.put("releasedToday", labRequests.countByStatusAndVerifiedAtBetween(LabStatus.VERIFIED, dayStart, dayEnd));
            }

            case WARD_STAFF -> {
                Instant wardDayStart = today.atStartOfDay(ZONE).toInstant();
                Instant wardDayEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
                long usable = beds.countUsable();
                long occupied = bedStays.countByToAtIsNull();
                c.put("inpatientsNow", admissions.countByStatus(AdmissionStatus.ADMITTED));
                c.put("freeBeds", Math.max(0L, usable - occupied));
                c.put("occupancyPercent", usable == 0 ? 0L : Math.round(occupied * 100.0 / usable));
                c.put("admittedToday", admissions.countByAdmittedAtBetweenAndStatusNot(
                        wardDayStart, wardDayEnd, AdmissionStatus.CANCELLED));
                c.put("dischargedToday", admissions.countByStatusAndDischargedAtBetween(
                        AdmissionStatus.DISCHARGED, wardDayStart, wardDayEnd));
            }

            default -> throw new ApiException(HttpStatus.FORBIDDEN, "There is no dashboard for your role");
        }
        return new DashboardResponse(role.name(), Instant.now(), c, queue);
    }

    private Map<String, Long> todayByStatus(LocalDate today) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (AppointmentStatus s : AppointmentStatus.values()) {
            map.put(s.name(), appointments.countByAppointmentDateAndStatus(today, s));
        }
        return map;
    }

    private void addPharmacyAlerts(Map<String, Object> c) {
        AlertsResponse a = pharmacy.alerts();
        c.put("lowStockMedicines", a.lowStock().size());
        c.put("nearExpiryBatches", a.nearExpiry().size());
        c.put("expiredBatches", a.expired().size());
    }

    private BigDecimal collectedToday(LocalDate today) {
        Instant from = today.atStartOfDay(ZONE).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        return nz(payments.netCollected(from, to));
    }

    private List<QueueItem> queueFor(LocalDate today, List<AppointmentStatus> statuses, boolean anyDoctor,
                                     Long doctorId, boolean withVitals) {
        return appointments.search(today, today, statuses, anyDoctor, doctorId, true, 0L, PageRequest.of(0, 50))
                .stream()
                .map(a -> new QueueItem(a.getId(), a.getStartTime(), a.getPatient().getPatientNo(),
                        a.getPatient().getFullName(), a.getDoctor().getUser().getFullName(), a.getStatus(),
                        vitalsFlag(withVitals, a.getId())))
                .toList();
    }

    private Boolean vitalsFlag(boolean withVitals, Long appointmentId) {
        if (!withVitals) return null;
        return vitals.findFirstByAppointmentIdOrderByRecordedAtDesc(appointmentId).isPresent();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
package backend.ward;

import backend.appointment.Appointment;
import backend.appointment.AppointmentRepository;
import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.doctor.Doctor;
import backend.doctor.DoctorRepository;
import backend.notification.NotificationService;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import backend.ward.WardDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdmissionService {

    private final AdmissionRepository admissions;
    private final BedRepository beds;
    private final BedStayRepository stays;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    // ---------- admitting ----------

    @Transactional
    public AdmissionResponse admit(AdmissionRequest r, Long actorId) {
        Patient p = patients.findById(r.patientId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
        if (!p.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
        }
        if (admissions.existsByPatientIdAndStatus(p.getId(), AdmissionStatus.ADMITTED)) {
            throw new ApiException(HttpStatus.CONFLICT, "This patient is already admitted");
        }
        Doctor d = doctors.findById(r.responsibleDoctorId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
        if (!d.isActive() || !d.getUser().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This doctor is not available");
        }
        Bed bed = lockFreeBed(r.bedId());
        if (r.appointmentId() != null) {
            Appointment ap = appointments.findById(r.appointmentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
            if (!ap.getPatient().getId().equals(p.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "That appointment belongs to a different patient");
            }
        }
        User staff = user(actorId);
        Instant now = Instant.now();

        Admission a = new Admission();
        a.setAdmissionNo(String.format("AD-%06d", admissions.nextNumber()));
        a.setPatient(p);
        a.setResponsibleDoctor(d);
        a.setAppointmentId(r.appointmentId());
        a.setReason(r.reason().trim());
        a.setAdmittedAt(now);
        a.setAdmittedBy(staff);
        a.getStays().add(newStay(a, bed, staff, now, null));
        saveChecked(a);

        String where = bed.getWard().getName() + ", bed " + bed.getBedNo();
        notifications.toUser(d.getUser().getId(), "ADMISSION",
                "Patient " + p.getPatientNo() + " was admitted under your care (" + where + ")", "/wards");
        notifications.toRole(Role.NURSE, "ADMISSION",
                "New admission: patient " + p.getPatientNo() + " in " + where, "/wards");
        audit.log(actorId, "PATIENT_ADMITTED", "ADMISSION", a.getId(), a.getAdmissionNo() + ", " + where);
        return toResponse(a, actorId, Role.WARD_STAFF);
    }

    @Transactional
    public AdmissionResponse transfer(Long id, TransferRequest r, Long actorId) {
        Admission a = lockActive(id);
        BedStay open = stays.findByAdmissionIdAndToAtIsNull(id)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "This admission has no current bed"));
        if (open.getBed().getId().equals(r.bedId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The patient is already in this bed");
        }
        Bed target = lockFreeBed(r.bedId());
        User staff = user(actorId);
        Instant now = Instant.now();

        // close the old stay first, so the database never sees two open stays at once
        open.setToAt(now);
        stays.saveAndFlush(open);
        a.getStays().add(newStay(a, target, staff, now, r.reason().trim()));
        saveChecked(a);

        String where = target.getWard().getName() + ", bed " + target.getBedNo();
        notifications.toUser(a.getResponsibleDoctor().getUser().getId(), "TRANSFER",
                "Patient " + a.getPatient().getPatientNo() + " was moved to " + where, "/wards");
        audit.log(actorId, "PATIENT_TRANSFERRED", "ADMISSION", id, a.getAdmissionNo() + " to " + where);
        return toResponse(a, actorId, Role.WARD_STAFF);
    }

    // ---------- discharge ----------

    @Transactional
    public AdmissionResponse saveSummary(Long id, SummaryRequest r, Long actorId) {
        Admission a = lockActive(id);
        requireResponsibleDoctor(a, actorId);
        boolean first = a.getDischargeSummary() == null || a.getDischargeSummary().isBlank();
        a.setDischargeSummary(r.summary().trim());
        a.setDischargeInstructions(clean(r.instructions()));
        a.setSummaryBy(user(actorId));
        a.setSummaryAt(Instant.now());
        if (first) {
            notifications.toRole(Role.WARD_STAFF, "DISCHARGE_READY",
                    "Discharge summary is ready for patient " + a.getPatient().getPatientNo()
                            + " (" + a.getAdmissionNo() + ")", "/wards");
        }
        audit.log(actorId, "DISCHARGE_SUMMARY_SAVED", "ADMISSION", id, a.getAdmissionNo());
        return toResponse(a, actorId, Role.DOCTOR);
    }

    @Transactional
    public AdmissionResponse discharge(Long id, Long actorId, Role role) {
        Admission a = lockActive(id);
        if (role == Role.DOCTOR) requireResponsibleDoctor(a, actorId);
        if (a.getDischargeSummary() == null || a.getDischargeSummary().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The doctor must write a discharge summary first");
        }
        Instant now = Instant.now();
        stays.findByAdmissionIdAndToAtIsNull(id).ifPresent(s -> s.setToAt(now));
        a.setStatus(AdmissionStatus.DISCHARGED);
        a.setDischargedAt(now);
        a.setDischargedBy(user(actorId));

        notifications.toRole(Role.CASHIER, "INVOICE_READY",
                "Patient " + a.getPatient().getPatientNo() + " was discharged. The admission invoice can be prepared.",
                "/billing");
        audit.log(actorId, "PATIENT_DISCHARGED", "ADMISSION", id, a.getAdmissionNo());
        return toResponse(a, actorId, role);
    }

    @Transactional
    public AdmissionResponse cancel(Long id, String reason, Long actorId) {
        Admission a = lockActive(id);
        if (a.getDischargeSummary() != null && !a.getDischargeSummary().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A discharge summary has already been written. Discharge the patient instead.");
        }
        Instant now = Instant.now();
        stays.findByAdmissionIdAndToAtIsNull(id).ifPresent(s -> s.setToAt(now));
        a.setStatus(AdmissionStatus.CANCELLED);
        a.setCancelReason(reason.trim());
        audit.log(actorId, "ADMISSION_CANCELLED", "ADMISSION", id, a.getAdmissionNo() + ", reason=" + reason.trim());
        return toResponse(a, actorId, Role.WARD_STAFF);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public PagedResponse<AdmissionSummary> list(String q, String status, int page, int size, Long actorId, Role role) {
        List<AdmissionStatus> statuses;
        if (status == null || status.isBlank()) {
            statuses = List.of(AdmissionStatus.ADMITTED);
        } else if ("ALL".equalsIgnoreCase(status)) {
            statuses = List.of(AdmissionStatus.values());
        } else {
            try {
                statuses = List.of(AdmissionStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status");
            }
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        boolean anyDoctor = true;
        long doctorId = 0L;
        if (role == Role.DOCTOR) {
            Doctor me = doctors.findByUserId(actorId).orElse(null);
            if (me == null) return new PagedResponse<>(List.of(), 0, safeSize, 0, 0);
            anyDoctor = false;
            doctorId = me.getId();
        }
        String cleaned = q == null ? "" : q.trim().replaceAll("[%_\\\\]", "");
        String pattern = "%" + cleaned.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Order.desc("admittedAt"), Sort.Order.desc("id")));
        return PagedResponse.of(admissions.search(statuses, anyDoctor, doctorId, pattern, pageable).map(this::summary));
    }

    @Transactional(readOnly = true)
    public AdmissionResponse get(Long id, Long actorId, Role role) {
        Admission a = admissions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admission not found"));
        return toResponse(a, actorId, role);
    }

    // ---------- helpers ----------

    private Admission lockActive(Long id) {
        Admission a = admissions.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admission not found"));
        if (a.getStatus() != AdmissionStatus.ADMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This admission is already " + a.getStatus().name().toLowerCase());
        }
        return a;
    }

    private Bed lockFreeBed(Long bedId) {
        Bed b = beds.lockById(bedId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bed not found"));
        if (!b.isActive() || !b.getWard().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This bed is not in service");
        }
        if (stays.existsByBedIdAndToAtIsNull(b.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This bed is already occupied");
        }
        return b;
    }

    private BedStay newStay(Admission a, Bed bed, User staff, Instant now, String reason) {
        BedStay s = new BedStay();
        s.setAdmission(a);
        s.setBed(bed);
        s.setWardName(bed.getWard().getName());
        s.setBedNo(bed.getBedNo());
        s.setDailyRate(bed.getWard().getDailyRate());
        s.setFromAt(now);
        s.setTransferReason(reason);
        s.setMovedBy(staff);
        return s;
    }

    private void saveChecked(Admission a) {
        try {
            admissions.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "The bed or the patient was just taken by another admission. Please check and try again.");
        }
    }

    private void requireResponsibleDoctor(Admission a, Long actorId) {
        if (!a.getResponsibleDoctor().getUser().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the responsible doctor can do this");
        }
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }

    private BedStay current(Admission a) {
        List<BedStay> list = a.getStays();
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private boolean hasSummary(Admission a) {
        return a.getDischargeSummary() != null && !a.getDischargeSummary().isBlank();
    }

    private AdmissionSummary summary(Admission a) {
        BedStay s = current(a);
        return new AdmissionSummary(a.getId(), a.getAdmissionNo(), a.getPatient().getPatientNo(),
                a.getPatient().getFullName(), a.getResponsibleDoctor().getUser().getFullName(),
                s == null ? null : s.getWardName(), s == null ? null : s.getBedNo(), a.getAdmittedAt(),
                a.getStatus(), a.getDischargedAt(), hasSummary(a));
    }

    private AdmissionResponse toResponse(Admission a, Long actorId, Role role) {
        boolean ownDoctor = role == Role.DOCTOR && a.getResponsibleDoctor().getUser().getId().equals(actorId);
        if (role == Role.DOCTOR && !ownDoctor) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This admission belongs to another doctor");
        }
        boolean seeReason = ownDoctor || role == Role.NURSE || role == Role.WARD_STAFF;
        boolean seeSummary = ownDoctor || role == Role.NURSE;
        boolean seeInstructions = seeSummary || role == Role.WARD_STAFF;
        BedStay cur = current(a);
        List<StayResponse> stayList = a.getStays().stream()
                .map(s -> new StayResponse(s.getId(), s.getWardName(), s.getBedNo(), s.getDailyRate(),
                        s.getFromAt(), s.getToAt(), s.getTransferReason()))
                .toList();
        return new AdmissionResponse(a.getId(), a.getAdmissionNo(), a.getPatient().getId(),
                a.getPatient().getPatientNo(), a.getPatient().getFullName(), a.getResponsibleDoctor().getId(),
                a.getResponsibleDoctor().getUser().getFullName(), seeReason ? a.getReason() : null, a.getStatus(),
                a.getAdmittedAt(), a.getAdmittedBy().getFullName(), cur == null ? null : cur.getWardName(),
                cur == null ? null : cur.getBedNo(), a.getDischargedAt(),
                a.getDischargedBy() == null ? null : a.getDischargedBy().getFullName(), hasSummary(a),
                seeSummary ? a.getDischargeSummary() : null,
                seeInstructions ? a.getDischargeInstructions() : null, a.getCancelReason(), stayList);
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
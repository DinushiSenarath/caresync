package backend.appointment;

import backend.appointment.AppointmentDtos.*;
import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.doctor.Doctor;
import backend.doctor.DoctorRepository;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final Set<Role> STAFF = Set.of(Role.ADMIN, Role.RECEPTIONIST);

    private final AppointmentRepository repo;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final AuditService audit;
    private final NotificationService notifications;

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public PagedResponse<AppointmentResponse> search(LocalDate date, LocalDate from, LocalDate to, Long doctorId,
                                                     AppointmentStatus status, Long patientId, int page, int size,
                                                     Long actorId, Role role) {
        LocalDate today = today();
        LocalDate f;
        LocalDate t;
        if (role == Role.NURSE) {
            f = today; t = today;
        } else if (date != null) {
            f = date; t = date;
        } else if (from != null || to != null) {
            f = from != null ? from : MIN_DATE;
            t = to != null ? to : MAX_DATE;
        } else {
            f = today; t = today;
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        Long doctorFilter = doctorId;
        if (role == Role.DOCTOR) {
            Doctor me = doctors.findByUserId(actorId).orElse(null);
            if (me == null) {
                return new PagedResponse<>(List.of(), 0, safeSize, 0, 0);
            }
            doctorFilter = me.getId();
        }

        List<AppointmentStatus> statuses = status != null ? List.of(status) : List.of(AppointmentStatus.values());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return PagedResponse.of(repo.search(f, t, statuses,
                doctorFilter == null, doctorFilter == null ? 0L : doctorFilter,
                patientId == null, patientId == null ? 0L : patientId,
                pageable).map(AppointmentResponse::from));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id, Long actorId, Role role) {
        Appointment a = find(id);
        if (role == Role.DOCTOR && !a.getDoctor().getUser().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This appointment belongs to another doctor");
        }
        if (role == Role.NURSE && !a.getAppointmentDate().equals(today())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nurses can only see today's appointments");
        }
        return AppointmentResponse.from(a);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> slots(Long doctorId, LocalDate date) {
        Doctor d = findDoctor(doctorId);
        if (!d.isActive() || !d.getUser().isActive() || !worksOn(d, date)) {
            return List.of();
        }
        Set<LocalTime> booked = new HashSet<>(repo.bookedTimes(doctorId, date, AppointmentStatus.CANCELLED));
        LocalDate today = today();
        LocalTime now = LocalTime.now(ZONE);
        int slot = d.getSlotMinutes();
        long total = Duration.between(d.getStartTime(), d.getEndTime()).toMinutes();
        List<SlotResponse> out = new ArrayList<>();
        for (long offset = 0; offset + slot <= total; offset += slot) {
            LocalTime time = d.getStartTime().plusMinutes(offset);
            boolean past = date.isBefore(today) || (date.equals(today) && time.isBefore(now));
            out.add(new SlotResponse(time, !past && !booked.contains(time)));
        }
        return out;
    }

    // ---------- writing ----------

    @Transactional
    public AppointmentResponse book(BookAppointmentRequest r, Long actorId) {
        Patient p = patients.findById(r.patientId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
        if (!p.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
        }
        Doctor d = findDoctor(r.doctorId());
        checkSlot(d, r.date(), r.time(), 0L);

        Appointment a = new Appointment();
        a.setPatient(p);
        a.setDoctor(d);
        a.setAppointmentDate(r.date());
        a.setStartTime(r.time());
        a.setStatus(AppointmentStatus.SCHEDULED);
        a.setReason(r.reason());
        a.setCreatedBy(actorId);
        saveChecked(a);
        audit.log(actorId, "APPOINTMENT_BOOKED", "APPOINTMENT", a.getId(),
                "patientId=" + p.getId() + ", doctorId=" + d.getId() + ", " + r.date() + " " + r.time());
        return AppointmentResponse.from(a);
    }

    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest r, Long actorId) {
        Appointment a = find(id);
        if (a.getStatus() != AppointmentStatus.SCHEDULED && a.getStatus() != AppointmentStatus.REQUESTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only requested or scheduled appointments can be rescheduled");
        }
        Doctor d = findDoctor(r.doctorId());
        checkSlot(d, r.date(), r.time(), a.getId());
        a.setDoctor(d);
        a.setAppointmentDate(r.date());
        a.setStartTime(r.time());
        saveChecked(a);
        audit.log(actorId, "APPOINTMENT_RESCHEDULED", "APPOINTMENT", id,
                "doctorId=" + d.getId() + ", " + r.date() + " " + r.time());
        return AppointmentResponse.from(a);
    }

    @Transactional
    public AppointmentResponse changeStatus(Long id, StatusChangeRequest r, Long actorId, Role role) {
        Appointment a = find(id);
        AppointmentStatus from = a.getStatus();
        AppointmentStatus to = r.status();

        Set<Role> allowed = rolesFor(from, to);
        if (allowed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot change status from " + from + " to " + to);
        }
        if (!allowed.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your role cannot make this change");
        }
        if (role == Role.DOCTOR && !a.getDoctor().getUser().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This appointment belongs to another doctor");
        }
        if (to == AppointmentStatus.CANCELLED && (r.reason() == null || r.reason().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required to cancel or reject");
        }
        if (to == AppointmentStatus.CHECKED_IN && !a.getAppointmentDate().equals(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Check-in is only possible on the appointment date");
        }
        if (to == AppointmentStatus.MISSED && a.getAppointmentDate().isAfter(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A future appointment cannot be marked as missed");
        }

        a.setStatus(to);
        if (to == AppointmentStatus.CANCELLED) {
            a.setCancelReason(r.reason().trim());
        }
        audit.log(actorId, "APPOINTMENT_STATUS_CHANGED", "APPOINTMENT", id, from + " -> " + to);
        notifyStatusChange(a, from, to);
        return AppointmentResponse.from(a);
    }

        @Transactional
    public AppointmentResponse requestAsPatient(Patient patient, Long doctorId, LocalDate date, LocalTime time,
                                                String reason, Long actorId) {
        if (!patient.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
        }
        if (repo.countByPatientIdAndStatus(patient.getId(), AppointmentStatus.REQUESTED) >= 3) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You already have 3 requests waiting. Please wait for the hospital to respond.");
        }
        Doctor d = findDoctor(doctorId);
        checkSlot(d, date, time, 0L);

        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setDoctor(d);
        a.setAppointmentDate(date);
        a.setStartTime(time);
        a.setStatus(AppointmentStatus.REQUESTED);
        a.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        a.setCreatedBy(actorId);
        saveChecked(a);
        audit.log(actorId, "APPOINTMENT_REQUESTED", "APPOINTMENT", a.getId(),
                "doctorId=" + d.getId() + ", " + date + " " + time);
        notifications.toRole(Role.RECEPTIONIST, "APPOINTMENT_REQUEST",
                "New appointment request from " + patient.getPatientNo() + " for " + date + " " + time,
                "/appointments/" + a.getId());
        return AppointmentResponse.from(a);
    }

    @Transactional
    public AppointmentResponse cancelAsPatient(Long appointmentId, Long patientId, String reason, Long actorId) {
        Appointment a = find(appointmentId);
        if (!a.getPatient().getId().equals(patientId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        if (a.getStatus() != AppointmentStatus.REQUESTED && a.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This appointment can no longer be cancelled online");
        }
        if (!a.getAppointmentDate().isAfter(today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Appointments can only be cancelled online before the day of the visit. Please call the hospital.");
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelReason(reason == null || reason.isBlank() ? "Cancelled by patient" : reason.trim());
        audit.log(actorId, "APPOINTMENT_CANCELLED_BY_PATIENT", "APPOINTMENT", appointmentId, null);
        return AppointmentResponse.from(a);
    }

    // ---------- helpers ----------
    private void notifyStatusChange(Appointment a, AppointmentStatus from, AppointmentStatus to) {
        String when = a.getAppointmentDate() + " " + a.getStartTime();
        Long patientUserId = a.getPatient().getUserId();
        if (from == AppointmentStatus.REQUESTED && patientUserId != null) {
            if (to == AppointmentStatus.SCHEDULED) {
                notifications.toUser(patientUserId, "REQUEST_CONFIRMED",
                        "Your appointment request for " + when + " was confirmed.", "/portal/appointments");
            } else if (to == AppointmentStatus.CANCELLED) {
                notifications.toUser(patientUserId, "REQUEST_REJECTED",
                        "Your appointment request for " + when + " could not be accepted. Please contact the hospital.",
                        "/portal/appointments");
            }
        }
        if (to == AppointmentStatus.CHECKED_IN) {
            String text = "Patient " + a.getPatient().getPatientNo() + " has checked in for " + a.getStartTime();
            String link = "/appointments/" + a.getId();
            notifications.toRole(Role.NURSE, "PATIENT_CHECKED_IN", text, link);
            notifications.toUser(a.getDoctor().getUser().getId(), "PATIENT_CHECKED_IN", text, link);
        }
    }
    private Set<Role> rolesFor(AppointmentStatus from, AppointmentStatus to) {
        return switch (from) {
            case REQUESTED -> (to == AppointmentStatus.SCHEDULED || to == AppointmentStatus.CANCELLED)
                    ? STAFF : Set.<Role>of();
            case SCHEDULED -> (to == AppointmentStatus.CHECKED_IN || to == AppointmentStatus.CANCELLED
                    || to == AppointmentStatus.MISSED) ? STAFF : Set.<Role>of();
            case CHECKED_IN -> to == AppointmentStatus.IN_PROGRESS ? Set.of(Role.DOCTOR) : Set.<Role>of();
            case IN_PROGRESS -> to == AppointmentStatus.COMPLETED ? Set.of(Role.DOCTOR) : Set.<Role>of();
            default -> Set.<Role>of();
        };
    }

    private void checkSlot(Doctor d, LocalDate date, LocalTime time, Long excludeId) {
        if (!d.isActive() || !d.getUser().isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This doctor is not available");
        }
        LocalDate today = today();
        if (date.isBefore(today) || (date.equals(today) && time.isBefore(LocalTime.now(ZONE)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The appointment time is in the past");
        }
        if (!worksOn(d, date)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The doctor does not work on this day");
        }
        long offset = Duration.between(d.getStartTime(), time).toMinutes();
        boolean misaligned = time.getSecond() != 0 || time.getNano() != 0
                || offset < 0 || offset % d.getSlotMinutes() != 0
                || Duration.between(time, d.getEndTime()).toMinutes() < d.getSlotMinutes();
        if (misaligned) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Time must be a slot start within the doctor's hours (every " + d.getSlotMinutes() + " minutes)");
        }
        if (repo.slotTaken(d.getId(), date, time, AppointmentStatus.CANCELLED, excludeId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This time slot is already booked");
        }
    }

    private boolean worksOn(Doctor d, LocalDate date) {
        String day = date.getDayOfWeek().name().substring(0, 3);
        return Arrays.stream(d.getWorkDays().split(",")).map(String::trim).anyMatch(day::equals);
    }

    private void saveChecked(Appointment a) {
        try {
            repo.saveAndFlush(a);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This time slot has just been booked by someone else");
        }
    }

    private Appointment find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    private Doctor findDoctor(Long id) {
        return doctors.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
    }

    private LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
package backend.clinical;

import backend.appointment.Appointment;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.clinical.ClinicalDtos.*;
import backend.common.ApiException;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NursingService {

    private final VitalSignRepository vitals;
    private final NursingNoteRepository nursingNotes;
    private final UserRepository users;
    private final AppointmentAccess access;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public List<VitalResponse> listVitals(Long appointmentId, Long actorId, Role role) {
        access.load(appointmentId, actorId, role);
        return vitals.findByAppointmentIdOrderByRecordedAtDesc(appointmentId).stream()
                .map(VitalResponse::from).toList();
    }

    @Transactional
    public VitalResponse recordVitals(Long appointmentId, VitalRequest r, Long actorId) {
        Appointment a = access.load(appointmentId, actorId, Role.NURSE);
        if (a.getStatus() != AppointmentStatus.CHECKED_IN && a.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vitals can be recorded after the patient has checked in");
        }
        if (r.temperature() == null && r.pulse() == null && r.bpSystolic() == null && r.bpDiastolic() == null
                && r.respRate() == null && r.spo2() == null && r.weight() == null && r.height() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter at least one measurement");
        }
        if ((r.bpSystolic() == null) != (r.bpDiastolic() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter both systolic and diastolic blood pressure");
        }
        if (r.bpSystolic() != null && r.bpSystolic() <= r.bpDiastolic()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Systolic pressure must be higher than diastolic");
        }
        VitalSign v = new VitalSign();
        v.setAppointmentId(appointmentId);
        v.setRecordedBy(user(actorId));
        v.setTemperature(r.temperature());
        v.setPulse(r.pulse());
        v.setBpSystolic(r.bpSystolic());
        v.setBpDiastolic(r.bpDiastolic());
        v.setRespRate(r.respRate());
        v.setSpo2(r.spo2());
        v.setWeight(r.weight());
        v.setHeight(r.height());
        vitals.save(v);
        audit.log(actorId, "VITALS_RECORDED", "VITAL_SIGN", v.getId(), "appointmentId=" + appointmentId);
        notifications.toUser(a.getDoctor().getUser().getId(), "VITALS_RECORDED",
                "Vitals recorded for patient " + a.getPatient().getPatientNo(), "/appointments/" + appointmentId);
        return VitalResponse.from(v);
    }

    @Transactional(readOnly = true)
    public List<NursingNoteResponse> listNotes(Long appointmentId, Long actorId, Role role) {
        access.load(appointmentId, actorId, role);
        return nursingNotes.findByAppointmentIdOrderByCreatedAtAsc(appointmentId).stream()
                .map(NursingNoteResponse::from).toList();
    }

    @Transactional
    public NursingNoteResponse addNote(Long appointmentId, NursingNoteRequest r, Long actorId) {
        Appointment a = access.load(appointmentId, actorId, Role.NURSE);
        AppointmentStatus s = a.getStatus();
        if (s != AppointmentStatus.CHECKED_IN && s != AppointmentStatus.IN_PROGRESS && s != AppointmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Notes can be added after the patient has checked in");
        }
        NursingNote n = new NursingNote();
        n.setAppointmentId(appointmentId);
        n.setNurse(user(actorId));
        n.setNote(r.note().trim());
        nursingNotes.save(n);
        audit.log(actorId, "NURSING_NOTE_ADDED", "NURSING_NOTE", n.getId(), "appointmentId=" + appointmentId);
        return NursingNoteResponse.from(n);
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }
}
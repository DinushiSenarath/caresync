package backend.clinical;

import backend.appointment.Appointment;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.clinical.ClinicalDtos.*;
import backend.common.ApiException;
import backend.user.Role;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConsultationNoteService {

    private final ConsultationNoteRepository notes;
    private final UserRepository users;
    private final AppointmentAccess access;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public NoteResponse get(Long appointmentId, Long actorId, Role role) {
        access.load(appointmentId, actorId, role);
        ConsultationNote n = notes.findByAppointmentId(appointmentId).orElseThrow(this::noNote);
        if (role == Role.NURSE && n.getStatus() != NoteStatus.FINAL) {
            throw noNote();
        }
        return NoteResponse.from(n);
    }

    @Transactional
    public NoteResponse save(Long appointmentId, NoteRequest r, Long actorId) {
        Appointment a = access.load(appointmentId, actorId, Role.DOCTOR);
        requireStage(a);
        if (blank(r.symptoms()) && blank(r.findings()) && blank(r.diagnosis()) && blank(r.treatmentPlan())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter at least one field");
        }
        ConsultationNote n = notes.findByAppointmentId(appointmentId).orElseGet(ConsultationNote::new);
        if (n.getStatus() == NoteStatus.FINAL) {
            throw new ApiException(HttpStatus.CONFLICT, "A final note cannot be edited");
        }
        if (n.getId() == null) {
            n.setAppointmentId(appointmentId);
            n.setAuthor(users.findById(actorId)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available")));
        }
        n.setSymptoms(r.symptoms());
        n.setFindings(r.findings());
        n.setDiagnosis(r.diagnosis());
        n.setTreatmentPlan(r.treatmentPlan());
        notes.save(n);
        audit.log(actorId, "NOTE_SAVED", "CONSULTATION_NOTE", n.getId(), "appointmentId=" + appointmentId);
        return NoteResponse.from(n);
    }

    @Transactional
    public NoteResponse finalise(Long appointmentId, Long actorId) {
        Appointment a = access.load(appointmentId, actorId, Role.DOCTOR);
        requireStage(a);
        ConsultationNote n = notes.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Save the note first"));
        if (n.getStatus() == NoteStatus.FINAL) {
            throw new ApiException(HttpStatus.CONFLICT, "This note is already final");
        }
        if (blank(n.getDiagnosis())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A diagnosis is required to finalise the note");
        }
        n.setStatus(NoteStatus.FINAL);
        n.setFinalisedAt(Instant.now());
        audit.log(actorId, "NOTE_FINALISED", "CONSULTATION_NOTE", n.getId(), "appointmentId=" + appointmentId);
        return NoteResponse.from(n);
    }

    private void requireStage(Appointment a) {
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS && a.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Start the consultation (status In progress) before writing notes");
        }
    }

    private ApiException noNote() {
        return new ApiException(HttpStatus.NOT_FOUND, "No note found for this appointment");
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
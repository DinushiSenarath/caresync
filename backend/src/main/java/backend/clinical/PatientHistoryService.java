package backend.clinical;

import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.clinical.ClinicalDtos.*;
import backend.common.ApiException;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatientHistoryService {

    private static final Set<AppointmentStatus> SEEN =
            Set.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final ConsultationNoteRepository notes;
    private final VitalSignRepository vitals;
    private final AuditService audit;

    @Transactional
    public HistoryResponse history(Long patientId, Long actorId) {
        Patient p = patients.findById(patientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
        List<VisitHistory> visits = appointments
                .findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patientId, PageRequest.of(0, 30))
                .stream()
                .filter(a -> SEEN.contains(a.getStatus()))
                .map(a -> new VisitHistory(
                        a.getId(), a.getAppointmentDate(), a.getStartTime(), a.getStatus(),
                        a.getDoctor().getUser().getFullName(),
                        notes.findByAppointmentId(a.getId())
                                .filter(n -> n.getStatus() == NoteStatus.FINAL)
                                .map(NoteResponse::from).orElse(null),
                        vitals.findFirstByAppointmentIdOrderByRecordedAtDesc(a.getId())
                                .map(VitalResponse::from).orElse(null)))
                .toList();
        audit.log(actorId, "PATIENT_HISTORY_VIEWED", "PATIENT", patientId, null);
        return new HistoryResponse(
                new PatientSummary(p.getId(), p.getPatientNo(), p.getFullName(), p.getDateOfBirth(),
                        p.getGender(), p.getAllergies()),
                visits);
    }
}
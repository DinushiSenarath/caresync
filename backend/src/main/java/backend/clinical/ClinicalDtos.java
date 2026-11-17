package backend.clinical;

import backend.appointment.AppointmentStatus;
import backend.patient.Gender;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ClinicalDtos {
    private ClinicalDtos() {}

    // ----- consultation notes -----
    public record NoteRequest(
            @Size(max = 4000) String symptoms,
            @Size(max = 4000) String findings,
            @Size(max = 4000) String diagnosis,
            @Size(max = 4000) String treatmentPlan) {}

    public record NoteResponse(Long id, Long appointmentId, String authorName, String symptoms, String findings,
                               String diagnosis, String treatmentPlan, NoteStatus status,
                               Instant finalisedAt, Instant updatedAt) {
        public static NoteResponse from(ConsultationNote n) {
            return new NoteResponse(n.getId(), n.getAppointmentId(), n.getAuthor().getFullName(),
                    n.getSymptoms(), n.getFindings(), n.getDiagnosis(), n.getTreatmentPlan(),
                    n.getStatus(), n.getFinalisedAt(), n.getUpdatedAt());
        }
    }

    // ----- vital signs -----
    public record VitalRequest(
            @DecimalMin("30.0") @DecimalMax("45.0") BigDecimal temperature,
            @Min(20) @Max(250) Integer pulse,
            @Min(40) @Max(300) Integer bpSystolic,
            @Min(20) @Max(200) Integer bpDiastolic,
            @Min(4) @Max(80) Integer respRate,
            @Min(50) @Max(100) Integer spo2,
            @DecimalMin("0.5") @DecimalMax("500.0") BigDecimal weight,
            @DecimalMin("20.0") @DecimalMax("260.0") BigDecimal height) {}

    /** Flags are prompts based on adult reference ranges. They are not a diagnosis. */
    public record VitalResponse(Long id, Long appointmentId, String recordedBy, BigDecimal temperature,
                                Integer pulse, Integer bpSystolic, Integer bpDiastolic, Integer respRate,
                                Integer spo2, BigDecimal weight, BigDecimal height, Instant recordedAt,
                                List<String> flags) {
        public static VitalResponse from(VitalSign v) {
            List<String> f = new ArrayList<>();
            if (v.getTemperature() != null) {
                double t = v.getTemperature().doubleValue();
                if (t >= 38.0) f.add("TEMPERATURE_HIGH");
                else if (t < 35.0) f.add("TEMPERATURE_LOW");
            }
            if (v.getPulse() != null) {
                if (v.getPulse() > 100) f.add("PULSE_HIGH");
                else if (v.getPulse() < 50) f.add("PULSE_LOW");
            }
            if (v.getBpSystolic() != null) {
                if (v.getBpSystolic() >= 140) f.add("SYSTOLIC_HIGH");
                else if (v.getBpSystolic() < 90) f.add("SYSTOLIC_LOW");
            }
            if (v.getBpDiastolic() != null) {
                if (v.getBpDiastolic() >= 90) f.add("DIASTOLIC_HIGH");
                else if (v.getBpDiastolic() < 60) f.add("DIASTOLIC_LOW");
            }
            if (v.getRespRate() != null) {
                if (v.getRespRate() > 20) f.add("RESP_RATE_HIGH");
                else if (v.getRespRate() < 12) f.add("RESP_RATE_LOW");
            }
            if (v.getSpo2() != null && v.getSpo2() < 94) f.add("SPO2_LOW");
            return new VitalResponse(v.getId(), v.getAppointmentId(), v.getRecordedBy().getFullName(),
                    v.getTemperature(), v.getPulse(), v.getBpSystolic(), v.getBpDiastolic(), v.getRespRate(),
                    v.getSpo2(), v.getWeight(), v.getHeight(), v.getRecordedAt(), f);
        }
    }

    // ----- nursing notes -----
    public record NursingNoteRequest(@NotBlank @Size(max = 2000) String note) {}

    public record NursingNoteResponse(Long id, Long appointmentId, String nurseName, String note, Instant createdAt) {
        public static NursingNoteResponse from(NursingNote n) {
            return new NursingNoteResponse(n.getId(), n.getAppointmentId(), n.getNurse().getFullName(),
                    n.getNote(), n.getCreatedAt());
        }
    }

    // ----- patient history -----
    public record PatientSummary(Long id, String patientNo, String fullName, LocalDate dateOfBirth,
                                 Gender gender, String allergies) {}

    public record VisitHistory(Long appointmentId, LocalDate date, LocalTime startTime, AppointmentStatus status,
                               String doctorName, NoteResponse finalNote, VitalResponse latestVitals) {}

    public record HistoryResponse(PatientSummary patient, List<VisitHistory> visits) {}
}
package backend.prescription;

import backend.appointment.Appointment;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.clinical.AppointmentAccess;
import backend.common.ApiException;
import backend.pharmacy.Medicine;
import backend.pharmacy.MedicineRepository;
import backend.prescription.PrescriptionDtos.*;
import backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptions;
    private final MedicineRepository medicines;
    private final AppointmentAccess access;
    private final PrescriptionMapper mapper;
    private final AuditService audit;
    private final NotificationService notifications;

    @Transactional
    public PrescriptionResponse create(Long appointmentId, PrescriptionRequest r, Long actorId) {
        Appointment a = access.load(appointmentId, actorId, Role.DOCTOR);
        requireStage(a);
        Prescription p = new Prescription();
        p.setAppointment(a);
        p.setDoctor(a.getDoctor());
        p.setNotes(clean(r.notes()));
        setItems(p, r.items());
        prescriptions.save(p);
        audit.log(actorId, "RX_CREATED", "PRESCRIPTION", p.getId(), "appointmentId=" + appointmentId);
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listForAppointment(Long appointmentId, Long actorId, Role role) {
        access.load(appointmentId, actorId, role);
        return prescriptions.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .filter(p -> role != Role.NURSE || p.getStatus() != PrescriptionStatus.DRAFT)
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse get(Long id, Long actorId, Role role) {
        Prescription p = prescriptions.findById(id).orElseThrow(this::notFound);
        if (role == Role.PHARMACIST) {
            if (p.getStatus() == PrescriptionStatus.DRAFT) throw notFound();
            return mapper.toResponse(p);
        }
        access.load(p.getAppointment().getId(), actorId, role);
        if (role == Role.NURSE && p.getStatus() == PrescriptionStatus.DRAFT) throw notFound();
        return mapper.toResponse(p);
    }

    @Transactional
    public PrescriptionResponse update(Long id, PrescriptionRequest r, Long actorId) {
        Prescription p = ownDraft(id, actorId);
        p.setNotes(clean(r.notes()));
        p.getItems().clear();
        setItems(p, r.items());
        audit.log(actorId, "RX_UPDATED", "PRESCRIPTION", id, null);
        return mapper.toResponse(p);
    }

    @Transactional
    public PrescriptionResponse issue(Long id, Long actorId) {
        Prescription p = ownDraft(id, actorId);
        requireStage(p.getAppointment());
        for (PrescriptionItem i : p.getItems()) {
            if (!i.getMedicine().isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        i.getMedicine().getName() + " is no longer active. Edit the prescription first.");
            }
        }
        p.setStatus(PrescriptionStatus.ISSUED);
        p.setIssuedAt(Instant.now());
        audit.log(actorId, "RX_ISSUED", "PRESCRIPTION", id, "items=" + p.getItems().size());
        notifications.toRole(Role.PHARMACIST, "PRESCRIPTION_ISSUED",
                "A prescription for " + p.getAppointment().getPatient().getPatientNo() + " is ready to dispense",
                "/pharmacy/queue");
        return mapper.toResponse(p);
    }

    @Transactional
    public PrescriptionResponse cancel(Long id, String reason, Long actorId) {
        Prescription p = prescriptions.lockById(id).orElseThrow(this::notFound);
        checkOwner(p, actorId);
        if (p.getStatus() != PrescriptionStatus.DRAFT && p.getStatus() != PrescriptionStatus.ISSUED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A prescription that is " + p.getStatus() + " cannot be cancelled");
        }
        p.setStatus(PrescriptionStatus.CANCELLED);
        audit.log(actorId, "RX_CANCELLED", "PRESCRIPTION", id, "reason=" + reason.trim());
        return mapper.toResponse(p);
    }

    // ---------- helpers ----------

    private Prescription ownDraft(Long id, Long actorId) {
        Prescription p = prescriptions.findById(id).orElseThrow(this::notFound);
        checkOwner(p, actorId);
        if (p.getStatus() != PrescriptionStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Only a draft can be changed. Cancel this prescription and create a new one.");
        }
        return p;
    }

    private void checkOwner(Prescription p, Long actorId) {
        if (!p.getDoctor().getUser().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This prescription belongs to another doctor");
        }
    }

    private void setItems(Prescription p, List<ItemRequest> requests) {
        Set<Long> seen = new HashSet<>();
        for (ItemRequest r : requests) {
            if (!seen.add(r.medicineId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The same medicine is listed twice");
            }
            Medicine m = medicines.findById(r.medicineId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Medicine " + r.medicineId() + " not found"));
            if (!m.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, m.getName() + " is not active");
            }
            PrescriptionItem i = new PrescriptionItem();
            i.setPrescription(p);
            i.setMedicine(m);
            i.setDose(r.dose().trim());
            i.setRoute(clean(r.route()));
            i.setFrequency(r.frequency().trim());
            i.setDuration(clean(r.duration()));
            i.setInstructions(clean(r.instructions()));
            i.setQtyPrescribed(r.quantity());
            p.getItems().add(i);
        }
    }

    private void requireStage(Appointment a) {
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS && a.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Start the consultation (status In progress) before prescribing");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Prescription not found");
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
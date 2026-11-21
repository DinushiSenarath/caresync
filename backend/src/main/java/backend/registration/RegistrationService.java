package backend.registration;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.patient.Patient;
import backend.patient.PatientDtos.PatientRequest;
import backend.patient.PatientDtos.PatientResponse;
import backend.patient.PatientRepository;
import backend.patient.PatientService;
import backend.portal.ActivationService;
import backend.portal.ActivationService.IssuedCode;
import backend.registration.RegistrationDtos.*;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    public static final String PUBLIC_MESSAGE =
            "Thank you. Your request has been received. The hospital will contact you on the phone number "
                    + "you gave to complete your registration.";
    private static final int MAX_PENDING = 300;

    private final RegistrationRequestRepository requests;
    private final PatientRepository patients;
    private final PatientService patientService;
    private final ActivationService activation;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;

    // ---------- public submission (deliberately not @Transactional, see note in the guide) ----------

    public String submit(PublicRegistrationRequest r) {
        if (r.website() != null && !r.website().isBlank()) {
            return PUBLIC_MESSAGE; // hidden trap field: only bots fill it in
        }
        if (r.dateOfBirth().isBefore(LocalDate.of(1900, 1, 1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Date of birth is not valid");
        }
        if (requests.countByStatus(RegistrationStatus.PENDING) >= MAX_PENDING) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Online registration is busy right now. Please contact the hospital.");
        }
        String phone = r.phone().trim();
        String nic = clean(r.nic());
        if (nic != null) nic = nic.toUpperCase();

        if (requests.existsByStatusAndPhone(RegistrationStatus.PENDING, phone)
                || (nic != null && requests.existsByStatusAndNic(RegistrationStatus.PENDING, nic))) {
            return PUBLIC_MESSAGE; // already waiting: reply the same way so nothing is revealed
        }
        RegistrationRequest req = new RegistrationRequest();
        req.setFullName(r.fullName().trim());
        req.setDateOfBirth(r.dateOfBirth());
        req.setGender(r.gender());
        req.setNic(nic);
        req.setPhone(phone);
        req.setAddress(clean(r.address()));
        try {
            requests.saveAndFlush(req);
        } catch (DataIntegrityViolationException e) {
            return PUBLIC_MESSAGE; // two identical requests at the same moment
        }
        audit.log(null, "REGISTRATION_REQUESTED", "REGISTRATION_REQUEST", req.getId(), null);

        notifications.toRole(Role.RECEPTIONIST, "REGISTRATION_REQUEST",
                "A new online registration request is waiting for review", "/registration-requests");
        return PUBLIC_MESSAGE;
    }

    // ---------- staff ----------

    @Transactional(readOnly = true)
    public PagedResponse<RegistrationResponse> list(RegistrationStatus status, int page, int size) {
        List<RegistrationStatus> statuses = status != null ? List.of(status) : List.of(RegistrationStatus.values());
        Sort sort = status == RegistrationStatus.PENDING
                ? Sort.by(Sort.Direction.ASC, "createdAt", "id")
                : Sort.by(Sort.Direction.DESC, "createdAt", "id");
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return PagedResponse.of(requests.findByStatusIn(statuses, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RegistrationResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ApprovalResponse approve(Long id, ApproveRequest r, boolean confirmDuplicate, Long actorId, Role role) {
        RegistrationRequest req = requests.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        requirePending(req);
        User staff = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));

        boolean linked = r.existingPatientId() != null;
        Patient patient;
        if (linked) {
            patient = patients.findById(r.existingPatientId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
            if (!patient.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
            }
        } else {
            PatientResponse created = patientService.create(new PatientRequest(
                    req.getFullName(), req.getDateOfBirth(), req.getGender(), req.getNic(), req.getPhone(),
                    req.getAddress(), null, null, null), confirmDuplicate, actorId, role);
            patient = patients.findById(created.id()).orElseThrow();
        }

        boolean wantCode = r.issueActivationCode() == null || r.issueActivationCode();
        IssuedCode code = null;
        String message;
        if (!wantCode) {
            message = "No activation code was issued. You can issue one later from the patient record.";
        } else if (patient.getUserId() != null) {
            message = "This patient already has a portal account, so no code was issued.";
        } else {
            code = activation.issue(patient.getId(), actorId);
            message = "Give this activation code to the patient. It is shown only once.";
        }

        req.setStatus(RegistrationStatus.APPROVED);
        req.setPatientId(patient.getId());
        req.setDecisionNote(r.note().trim());
        req.setDecidedBy(staff);
        req.setDecidedAt(Instant.now());
        audit.log(actorId, "REGISTRATION_APPROVED", "REGISTRATION_REQUEST", id,
                "patientNo=" + patient.getPatientNo() + (linked ? ", linked to existing record" : ", new record"));
        return new ApprovalResponse(toResponse(req), patient.getId(), patient.getPatientNo(), linked, code, message);
    }

    @Transactional
    public RegistrationResponse reject(Long id, String note, Long actorId) {
        RegistrationRequest req = requests.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        requirePending(req);
        User staff = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
        req.setStatus(RegistrationStatus.REJECTED);
        req.setDecisionNote(note.trim());
        req.setDecidedBy(staff);
        req.setDecidedAt(Instant.now());
        audit.log(actorId, "REGISTRATION_REJECTED", "REGISTRATION_REQUEST", id, "note=" + note.trim());
        return toResponse(req);
    }

    // ---------- helpers ----------

    private RegistrationRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private void requirePending(RegistrationRequest req) {
        if (req.getStatus() != RegistrationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This request has already been " + req.getStatus().name().toLowerCase());
        }
    }

    private RegistrationResponse toResponse(RegistrationRequest r) {
        String patientNo = r.getPatientId() == null ? null
                : patients.findById(r.getPatientId()).map(Patient::getPatientNo).orElse(null);
        return new RegistrationResponse(r.getId(), r.getFullName(), r.getDateOfBirth(), r.getGender(), r.getNic(),
                r.getPhone(), r.getAddress(), r.getStatus(), r.getPatientId(), patientNo, r.getDecisionNote(),
                r.getDecidedBy() == null ? null : r.getDecidedBy().getFullName(), r.getCreatedAt(), r.getDecidedAt());
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
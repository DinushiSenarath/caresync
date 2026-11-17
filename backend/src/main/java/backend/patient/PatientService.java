package backend.patient;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.patient.PatientDtos.*;
import backend.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository repo;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PagedResponse<PatientResponse> search(String q, boolean includeInactive, int page, int size, Role role) {
        String cleaned = q == null ? "" : q.trim().replaceAll("[%_\\\\]", "");
        String pattern = "%" + cleaned.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(repo.search(pattern, includeInactive, pageable).map(p -> toResponse(p, role)));
    }

    @Transactional(readOnly = true)
    public PatientResponse get(Long id, Role role) {
        return toResponse(find(id), role);
    }

    @Transactional
    public PatientResponse create(PatientRequest r, boolean confirmDuplicate, Long actorId, Role role) {
        checkDuplicates(r, null, confirmDuplicate);
        Patient p = new Patient();
        p.setPatientNo(Patient.formatNumber(repo.nextSeq()));
        apply(p, r);
        p.setCreatedBy(actorId);
        repo.save(p);
        audit.log(actorId, "PATIENT_REGISTERED", "PATIENT", p.getId(), "patientNo=" + p.getPatientNo());
        return toResponse(p, role);
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest r, boolean confirmDuplicate, Long actorId, Role role) {
        Patient p = find(id);
        checkDuplicates(r, id, confirmDuplicate);
        apply(p, r);
        audit.log(actorId, "PATIENT_UPDATED", "PATIENT", id, "patientNo=" + p.getPatientNo());
        return toResponse(p, role);
    }

    @Transactional
    public PatientResponse setActive(Long id, boolean active, Long actorId, Role role) {
        Patient p = find(id);
        p.setActive(active);
        audit.log(actorId, active ? "PATIENT_REACTIVATED" : "PATIENT_DEACTIVATED", "PATIENT", id, null);
        return toResponse(p, role);
    }

    private Patient find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private void checkDuplicates(PatientRequest r, Long excludeId, boolean confirm) {
        String nic = clean(r.nic());
        List<DuplicateMatch> matches = repo
                .findPossibleDuplicates(nic == null ? "" : nic.toUpperCase(), r.fullName().trim(), r.dateOfBirth())
                .stream()
                .filter(p -> !Objects.equals(p.getId(), excludeId))
                .map(p -> new DuplicateMatch(p.getId(), p.getPatientNo(), p.getFullName(), p.getDateOfBirth(), p.getPhone()))
                .toList();
        if (!matches.isEmpty() && !confirm) {
            throw new DuplicatePatientException(matches);
        }
    }

    private void apply(Patient p, PatientRequest r) {
        p.setFullName(r.fullName().trim());
        p.setDateOfBirth(r.dateOfBirth());
        p.setGender(r.gender());
        String nic = clean(r.nic());
        p.setNic(nic == null ? null : nic.toUpperCase());
        p.setPhone(r.phone().trim());
        p.setAddress(clean(r.address()));
        p.setEmergencyContactName(clean(r.emergencyContactName()));
        p.setEmergencyContactPhone(clean(r.emergencyContactPhone()));
        p.setAllergies(clean(r.allergies()));
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private PatientResponse toResponse(Patient p, Role role) {
        if (role == Role.PHARMACIST) {
            return new PatientResponse(p.getId(), p.getPatientNo(), p.getFullName(), null, null, null, null, null,
                    null, null, p.getAllergies(), p.isActive());
        }
        if (role == Role.CASHIER || role == Role.LAB_TECH) {
            return new PatientResponse(p.getId(), p.getPatientNo(), p.getFullName(), null, null, null, null, null,
                    null, null, null, p.isActive());
        }
        return new PatientResponse(p.getId(), p.getPatientNo(), p.getFullName(), p.getDateOfBirth(), p.getGender(),
                p.getNic(), p.getPhone(), p.getAddress(), p.getEmergencyContactName(),
                p.getEmergencyContactPhone(), p.getAllergies(), p.isActive());
    }
}
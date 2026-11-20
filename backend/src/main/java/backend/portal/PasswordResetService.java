package backend.portal;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.portal.ResetDtos.PatientResetRequest;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0, O, 1, I, L
    private static final int CODE_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration VALIDITY = Duration.ofHours(24);
    private static final String GENERIC = "Invalid or expired reset details";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivationCodeRepository codes;
    private final PatientRepository patients;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @Transactional
    public ActivationService.IssuedCode issue(Long patientId, String verification, Long actorId) {
        Patient p = patients.findById(patientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
        if (!p.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
        }
        if (p.getUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This patient has no portal account yet. Issue an activation code instead.");
        }
        User u = users.findById(p.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The portal account was not found"));
        if (u.getRole() != Role.PATIENT || !u.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This portal account is deactivated. Ask an administrator to reactivate it first.");
        }

        Instant now = Instant.now();
        for (ActivationCode old : codes.lockOpenCodes(patientId, CodePurpose.RESET)) {
            old.setUsedAt(now);
        }
        String raw = randomCode();
        ActivationCode c = new ActivationCode();
        c.setPatientId(patientId);
        c.setPurpose(CodePurpose.RESET);
        c.setCodeHash(encoder.encode(raw));
        c.setExpiresAt(now.plus(VALIDITY));
        c.setCreatedBy(actorId);
        codes.save(c);

        audit.log(actorId, "PASSWORD_RESET_CODE_ISSUED", "PATIENT", patientId,
                "identity check: " + verification.trim());
        return new ActivationService.IssuedCode(p.getPatientNo(), raw.substring(0, 5) + "-" + raw.substring(5),
                c.getExpiresAt());
    }

    /** Failed attempts must be saved even though an exception is thrown, so rollback is switched off. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(PatientResetRequest r) {
        Patient p = patients.findByPatientNoIgnoreCase(r.patientNo().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, GENERIC));
        if (!p.isActive() || p.getUserId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        Instant now = Instant.now();
        List<ActivationCode> open = codes.lockOpenCodes(p.getId(), CodePurpose.RESET);
        if (open.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        ActivationCode c = open.get(0);
        if (c.getExpiresAt().isBefore(now) || c.getFailedAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        String entered = r.code().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (!encoder.matches(entered, c.getCodeHash())) {
            c.setFailedAttempts(c.getFailedAttempts() + 1);
            audit.log(null, "PASSWORD_RESET_FAILED", "PATIENT", p.getId(), "attempt " + c.getFailedAttempts());
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        User u = users.findById(p.getUserId())
                .filter(User::isActive)
                .filter(x -> x.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, GENERIC));

        u.setPasswordHash(encoder.encode(r.newPassword()));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        c.setUsedAt(now);
        audit.log(u.getId(), "PASSWORD_RESET_BY_CODE", "USER", u.getId(), null);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
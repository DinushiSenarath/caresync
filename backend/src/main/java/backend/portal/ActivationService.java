package backend.portal;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.portal.PortalDtos.ActivateRequest;
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
public class ActivationService {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0, O, 1, I, L
    private static final int CODE_LENGTH = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration VALIDITY = Duration.ofDays(7);
    private static final String GENERIC = "Invalid or expired activation details";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivationCodeRepository codes;
    private final PatientRepository patients;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public record IssuedCode(String patientNo, String code, Instant expiresAt) {}

    @Transactional
    public IssuedCode issue(Long patientId, Long actorId) {
        Patient p = patients.findById(patientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Patient not found"));
        if (!p.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This patient record is deactivated");
        }
        if (p.getUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This patient already has a portal account");
        }
        Instant now = Instant.now();
        for (ActivationCode old : codes.lockOpenCodes(patientId)) {
            old.setUsedAt(now);
        }
        String raw = randomCode();
        ActivationCode c = new ActivationCode();
        c.setPatientId(patientId);
        c.setCodeHash(encoder.encode(raw));
        c.setExpiresAt(now.plus(VALIDITY));
        c.setCreatedBy(actorId);
        codes.save(c);
        audit.log(actorId, "ACTIVATION_CODE_ISSUED", "PATIENT", patientId, null);
        return new IssuedCode(p.getPatientNo(), raw.substring(0, 5) + "-" + raw.substring(5), c.getExpiresAt());
    }

    /** Failed attempts must be saved even though an exception is thrown, so rollback is switched off. */
    @Transactional(noRollbackFor = ApiException.class)
    public void activate(ActivateRequest r) {
        Patient p = patients.findByPatientNoIgnoreCase(r.patientNo().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, GENERIC));
        if (!p.isActive() || p.getUserId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        Instant now = Instant.now();
        List<ActivationCode> open = codes.lockOpenCodes(p.getId());
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
            audit.log(null, "ACTIVATION_FAILED", "PATIENT", p.getId(), "attempt " + c.getFailedAttempts());
            throw new ApiException(HttpStatus.BAD_REQUEST, GENERIC);
        }
        String username = r.username().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "This username is taken. Please choose another.");
        }
        User u = new User();
        u.setUsername(username);
        u.setFullName(p.getFullName());
        u.setRole(Role.PATIENT);
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);

        p.setUserId(u.getId());
        c.setUsedAt(now);
        audit.log(u.getId(), "PORTAL_ACCOUNT_ACTIVATED", "PATIENT", p.getId(), null);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
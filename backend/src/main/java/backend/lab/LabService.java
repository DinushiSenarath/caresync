package backend.lab;

import backend.appointment.Appointment;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.clinical.AppointmentAccess;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.lab.LabDtos.*;
import backend.notification.NotificationService;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LabService {

    private static final BigDecimal MAX_NUMBER = new BigDecimal("1000000000");

    private final LabTestRepository tests;
    private final LabRequestRepository requests;
    private final UserRepository users;
    private final PatientRepository patients;
    private final AppointmentAccess access;
    private final NotificationService notifications;
    private final AuditService audit;

    // ---------- test catalogue ----------

    @Transactional(readOnly = true)
    public List<LabTestResponse> listTests(boolean activeOnly) {
        List<LabTest> list = activeOnly ? tests.findByActiveTrueOrderByNameAsc() : tests.findAllByOrderByNameAsc();
        return list.stream().map(LabTestResponse::from).toList();
    }

    @Transactional
    public LabTestResponse createTest(LabTestRequest r, Long actorId) {
        String name = r.name().trim();
        if (tests.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A test with this name already exists");
        }
        checkRange(r);
        LabTest t = new LabTest();
        apply(t, r, name);
        t.setActive(r.active() == null || r.active());
        tests.save(t);
        audit.log(actorId, "LAB_TEST_CREATED", "LAB_TEST", t.getId(), name);
        return LabTestResponse.from(t);
    }

    @Transactional
    public LabTestResponse updateTest(Long id, LabTestRequest r, Long actorId) {
        LabTest t = tests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Test not found"));
        String name = r.name().trim();
        if (tests.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A test with this name already exists");
        }
        checkRange(r);
        apply(t, r, name);
        if (r.active() != null) t.setActive(r.active());
        audit.log(actorId, "LAB_TEST_UPDATED", "LAB_TEST", id, name);
        return LabTestResponse.from(t);
    }

    // ---------- requesting (doctor) ----------

    @Transactional
    public RequestResponse createRequest(LabRequestCreate r, Long actorId) {
        Appointment a = access.load(r.appointmentId(), actorId, Role.DOCTOR);
        if (a.getStatus() != AppointmentStatus.IN_PROGRESS && a.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Start the consultation (status In progress) before requesting tests");
        }
        User doctor = user(actorId);

        LabRequest lr = new LabRequest();
        lr.setRequestNo(String.format("LR-%06d", requests.nextNumber()));
        lr.setAppointmentId(a.getId());
        lr.setPatient(a.getPatient());
        lr.setRequestedBy(doctor);
        lr.setPriority(r.priority() == null ? LabPriority.ROUTINE : r.priority());
        lr.setClinicalNotes(clean(r.clinicalNotes()));

        Set<Long> seen = new HashSet<>();
        for (Long testId : r.testIds()) {
            if (!seen.add(testId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The same test is listed twice");
            }
            LabTest t = tests.findById(testId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Test " + testId + " not found"));
            if (!t.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, t.getName() + " is not available");
            }
            LabRequestItem i = new LabRequestItem();
            i.setRequest(lr);
            i.setTestId(t.getId());
            i.setTestName(t.getName());
            i.setSpecimen(t.getSpecimen());
            i.setResultType(t.getResultType());
            i.setUnit(t.getUnit());
            i.setRefLow(t.getRefLow());
            i.setRefHigh(t.getRefHigh());
            i.setRefText(t.getRefText());
            i.setPrice(t.getPrice());
            lr.getItems().add(i);
        }
        requests.save(lr);

        boolean urgent = lr.getPriority() == LabPriority.URGENT;
        notifications.toRole(Role.LAB_TECH, "LAB_REQUEST",
                (urgent ? "URGENT lab request " : "New lab request ") + lr.getRequestNo()
                        + " for patient " + a.getPatient().getPatientNo(), "/laboratory");
        audit.log(actorId, "LAB_REQUESTED", "LAB_REQUEST", lr.getId(),
                lr.getRequestNo() + ", " + lr.getItems().size() + " test(s), " + lr.getPriority());
        return toResponse(lr, false);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> forAppointment(Long appointmentId, Long actorId) {
        access.load(appointmentId, actorId, Role.DOCTOR);
        return requests.findByAppointmentIdOrderByIdAsc(appointmentId).stream()
                .map(r -> toResponse(r, r.getStatus() == LabStatus.VERIFIED))
                .toList();
    }

    // ---------- worklist and detail ----------

    @Transactional(readOnly = true)
    public PagedResponse<RequestSummary> worklist(String q, String status, int page, int size) {
        List<LabStatus> statuses;
        if (status == null || status.isBlank()) {
            statuses = List.of(LabStatus.REQUESTED, LabStatus.COLLECTED);
        } else if ("ALL".equalsIgnoreCase(status)) {
            statuses = List.of(LabStatus.values());
        } else {
            try {
                statuses = List.of(LabStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status");
            }
        }
        boolean finished = statuses.stream().allMatch(s -> s == LabStatus.VERIFIED || s == LabStatus.CANCELLED);
        Sort sort = finished
                ? Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

        String cleaned = q == null ? "" : q.trim().replaceAll("[%_\\\\]", "");
        String pattern = "%" + cleaned.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return PagedResponse.of(requests.worklist(statuses, pattern, pageable).map(r ->
                new RequestSummary(r.getId(), r.getRequestNo(), r.getPatient().getPatientNo(),
                        r.getPatient().getFullName(), r.getPriority(), r.getStatus(),
                        r.getRequestedBy().getFullName(), r.getCreatedAt(), r.getItems().size())));
    }

    @Transactional(readOnly = true)
    public RequestResponse get(Long id, Long actorId, Role role) {
        LabRequest r = requests.findById(id).orElseThrow(this::notFound);
        if (role == Role.DOCTOR) {
            access.load(r.getAppointmentId(), actorId, Role.DOCTOR);
            return toResponse(r, r.getStatus() == LabStatus.VERIFIED);
        }
        return toResponse(r, true);
    }

    // ---------- laboratory work ----------

    @Transactional
    public RequestResponse collect(Long id, String sampleNo, Long actorId) {
        LabRequest r = requests.lockById(id).orElseThrow(this::notFound);
        if (r.getStatus() != LabStatus.REQUESTED) {
            throw new ApiException(HttpStatus.CONFLICT, "A sample can only be collected for a waiting request");
        }
        r.setStatus(LabStatus.COLLECTED);
        r.setCollectedBy(user(actorId));
        r.setCollectedAt(Instant.now());
        r.setSampleNo(clean(sampleNo));
        audit.log(actorId, "LAB_SAMPLE_COLLECTED", "LAB_REQUEST", id, r.getRequestNo());
        return toResponse(r, true);
    }

    @Transactional
    public RequestResponse saveResults(Long id, ResultsRequest req, Long actorId) {
        LabRequest r = requests.lockById(id).orElseThrow(this::notFound);
        if (r.getStatus() != LabStatus.COLLECTED) {
            throw new ApiException(HttpStatus.CONFLICT, r.getStatus() == LabStatus.VERIFIED
                    ? "These results have been released and cannot be changed"
                    : "Results can only be entered after the sample has been collected");
        }
        User tech = user(actorId);
        Instant now = Instant.now();
        Map<Long, LabRequestItem> byId = r.getItems().stream()
                .collect(Collectors.toMap(LabRequestItem::getId, i -> i));
        Set<Long> seen = new HashSet<>();

        for (ResultEntry e : req.results()) {
            LabRequestItem item = byId.get(e.itemId());
            if (item == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Item " + e.itemId() + " does not belong to this request");
            }
            if (!seen.add(e.itemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Item " + e.itemId() + " is listed twice");
            }
            String value = e.value() == null ? "" : e.value().trim();
            if (value.isEmpty()) {
                item.setResultValue(null);
                item.setResultNumber(null);
                item.setFlag(null);
                item.setResultComment(clean(e.comment()));
                item.setEnteredBy(null);
                item.setEnteredAt(null);
                continue;
            }
            if (item.getResultType() == ResultType.NUMERIC) {
                BigDecimal n;
                try {
                    n = new BigDecimal(value);
                } catch (NumberFormatException ex) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, item.getTestName() + ": enter a number");
                }
                if (n.abs().compareTo(MAX_NUMBER) >= 0 || n.scale() > 4) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            item.getTestName() + ": the number is too large or has more than 4 decimals");
                }
                item.setResultNumber(n);
                item.setResultValue(n.toPlainString());
                item.setFlag(flagFor(item, n));
            } else {
                item.setResultNumber(null);
                item.setResultValue(value);
                item.setFlag(null);
            }
            item.setResultComment(clean(e.comment()));
            item.setEnteredBy(tech);
            item.setEnteredAt(now);
        }
        audit.log(actorId, "LAB_RESULT_ENTERED", "LAB_REQUEST", id, r.getRequestNo());
        return toResponse(r, true);
    }

    @Transactional
    public RequestResponse verify(Long id, Long actorId) {
        LabRequest r = requests.lockById(id).orElseThrow(this::notFound);
        if (r.getStatus() != LabStatus.COLLECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a request with a collected sample can be released");
        }
        List<String> missing = r.getItems().stream()
                .filter(i -> i.getResultValue() == null)
                .map(LabRequestItem::getTestName)
                .toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Enter and save a result for every test first. Missing: " + String.join(", ", missing));
        }
        r.setStatus(LabStatus.VERIFIED);
        r.setVerifiedBy(user(actorId));
        r.setVerifiedAt(Instant.now());

        notifications.toUser(r.getRequestedBy().getId(), "LAB_RESULTS_READY",
                "Lab results are ready for patient " + r.getPatient().getPatientNo() + " (" + r.getRequestNo() + ")",
                "/appointments/" + r.getAppointmentId());
        notifications.toUser(r.getPatient().getUserId(), "LAB_RESULTS_READY",
                "Your laboratory results are available.", "/portal/lab");
        audit.log(actorId, "LAB_VERIFIED", "LAB_REQUEST", id, r.getRequestNo());
        return toResponse(r, true);
    }

    @Transactional
    public RequestResponse cancel(Long id, String reason, Long actorId, Role role) {
        LabRequest r = requests.lockById(id).orElseThrow(this::notFound);
        if (role == Role.DOCTOR) {
            access.load(r.getAppointmentId(), actorId, Role.DOCTOR);
            if (r.getStatus() != LabStatus.REQUESTED) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Only a request that is still waiting can be cancelled. Please contact the laboratory.");
            }
        } else if (r.getStatus() != LabStatus.REQUESTED && r.getStatus() != LabStatus.COLLECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request can no longer be cancelled");
        }
        r.setStatus(LabStatus.CANCELLED);
        r.setCancelReason(reason.trim());
        if (role == Role.DOCTOR) {
            notifications.toRole(Role.LAB_TECH, "LAB_CANCELLED",
                    "Lab request " + r.getRequestNo() + " was cancelled by the doctor", "/laboratory");
        } else {
            notifications.toUser(r.getRequestedBy().getId(), "LAB_CANCELLED",
                    "Lab request " + r.getRequestNo() + " was cancelled by the laboratory",
                    "/appointments/" + r.getAppointmentId());
        }
        audit.log(actorId, "LAB_CANCELLED", "LAB_REQUEST", id, r.getRequestNo() + ", reason=" + reason.trim());
        return toResponse(r, role != Role.DOCTOR);
    }

    // ---------- patient portal ----------

    @Transactional(readOnly = true)
    public List<RequestResponse> portalResults(Long userId) {
        Patient p = patients.findByUserId(userId)
                .filter(Patient::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "No active patient record is linked to this account"));
        return requests.findByPatientIdAndStatusOrderByVerifiedAtDesc(p.getId(), LabStatus.VERIFIED).stream()
                .map(r -> toResponse(r, true))
                .toList();
    }

    // ---------- helpers ----------

    private ResultFlag flagFor(LabRequestItem i, BigDecimal n) {
        if (i.getRefLow() == null && i.getRefHigh() == null) return null;
        if (i.getRefLow() != null && n.compareTo(i.getRefLow()) < 0) return ResultFlag.LOW;
        if (i.getRefHigh() != null && n.compareTo(i.getRefHigh()) > 0) return ResultFlag.HIGH;
        return ResultFlag.NORMAL;
    }

    private void checkRange(LabTestRequest r) {
        if (r.refLow() != null && r.refHigh() != null && r.refLow().compareTo(r.refHigh()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The lower limit cannot be above the upper limit");
        }
    }

    private void apply(LabTest t, LabTestRequest r, String name) {
        boolean numeric = r.resultType() == ResultType.NUMERIC;
        t.setName(name);
        t.setCategory(r.category());
        t.setSpecimen(r.specimen().trim());
        t.setResultType(r.resultType());
        t.setUnit(clean(r.unit()));
        t.setRefLow(numeric ? r.refLow() : null);
        t.setRefHigh(numeric ? r.refHigh() : null);
        t.setRefText(clean(r.refText()));
        t.setPrice(r.price().setScale(2, RoundingMode.HALF_UP));
    }

    private RequestResponse toResponse(LabRequest r, boolean showResults) {
        List<ItemResponse> items = r.getItems().stream().map(i -> new ItemResponse(
                i.getId(), i.getTestId(), i.getTestName(), i.getSpecimen(), i.getResultType(), i.getUnit(),
                i.getRefLow(), i.getRefHigh(), i.getRefText(),
                showResults ? i.getResultValue() : null,
                showResults ? i.getFlag() : null,
                showResults ? i.getResultComment() : null,
                showResults && i.getEnteredBy() != null ? i.getEnteredBy().getFullName() : null,
                showResults ? i.getEnteredAt() : null)).toList();
        return new RequestResponse(r.getId(), r.getRequestNo(), r.getAppointmentId(), r.getPatient().getId(),
                r.getPatient().getPatientNo(), r.getPatient().getFullName(), r.getRequestedBy().getFullName(),
                r.getPriority(), r.getClinicalNotes(), r.getStatus(), r.getSampleNo(),
                name(r.getCollectedBy()), r.getCollectedAt(), name(r.getVerifiedBy()), r.getVerifiedAt(),
                r.getCancelReason(), r.getCreatedAt(), items);
    }

    private String name(User u) {
        return u == null ? null : u.getFullName();
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Lab request not found");
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
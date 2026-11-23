package backend.hr;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.hr.HrDtos.*;
import backend.notification.NotificationService;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<LeaveBalance> balances(Long employeeId, int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        List<LeaveBalance> out = new ArrayList<>();
        for (LeaveType t : LeaveType.values()) {
            int used = leaves.sumDays(employeeId, t, LeaveStatus.APPROVED, start, end).intValue();
            int pending = leaves.sumDays(employeeId, t, LeaveStatus.PENDING, start, end).intValue();
            Integer ent = t.getEntitlement();
            out.add(new LeaveBalance(t, ent, used, pending, ent == null ? null : Math.max(0, ent - used - pending)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveResponse> mine(Long userId, int page, int size) {
        Employee e = employeeService.forUser(userId);
        return PagedResponse.of(leaves.search(List.of(LeaveStatus.values()), false, e.getId(), pageable(page, size))
                .map(LeaveResponse::from));
    }

    @Transactional(readOnly = true)
    public PagedResponse<LeaveResponse> list(String status, Long employeeId, int page, int size) {
        List<LeaveStatus> statuses;
        if (status == null || status.isBlank()) {
            statuses = List.of(LeaveStatus.PENDING);
        } else if ("ALL".equalsIgnoreCase(status)) {
            statuses = List.of(LeaveStatus.values());
        } else {
            try {
                statuses = List.of(LeaveStatus.valueOf(status.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status");
            }
        }
        return PagedResponse.of(leaves.search(statuses, employeeId == null, employeeId == null ? 0L : employeeId,
                pageable(page, size)).map(LeaveResponse::from));
    }

    // ---------- requesting ----------

    @Transactional
    public LeaveResponse request(Long userId, LeaveBody b) {
        Employee e = lock(employeeService.forUser(userId).getId());
        return create(e, b.leaveType(), b.fromDate(), b.toDate(), b.reason(), userId, false);
    }

    @Transactional
    public LeaveResponse record(RecordLeaveBody b, Long actorId) {
        Employee e = lock(b.employeeId());
        if (!e.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This employee is not active");
        }
        if (actorId.equals(e.getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot record your own leave. Ask another administrator.");
        }
        return create(e, b.leaveType(), b.fromDate(), b.toDate(), b.reason(), actorId, true);
    }

    // ---------- deciding ----------

    @Transactional
    public LeaveResponse decide(Long id, DecisionBody b, Long actorId) {
        LeaveRequest l = leaves.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        if (l.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This request has already been " + l.getStatus().name().toLowerCase());
        }
        if (actorId.equals(l.getEmployee().getUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own leave request. Ask another administrator.");
        }
        String note = clean(b.note());
        if (!b.approve() && note == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give a reason for rejecting this request");
        }
        l.setStatus(b.approve() ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
        l.setDecidedBy(user(actorId));
        l.setDecisionNote(note);
        l.setDecidedAt(Instant.now());
        notifications.toUser(l.getEmployee().getUserId(), "LEAVE_DECIDED",
                "Your " + nice(l.getLeaveType()) + " leave request for " + l.getFromDate() + " to " + l.getToDate()
                        + " was " + l.getStatus().name().toLowerCase(), "/my-work");
        audit.log(actorId, b.approve() ? "LEAVE_APPROVED" : "LEAVE_REJECTED", "LEAVE", id,
                l.getEmployee().getEmployeeNo() + ", " + l.getLeaveType() + " " + l.getFromDate() + " to " + l.getToDate());
        return LeaveResponse.from(l);
    }

    @Transactional
    public LeaveResponse cancel(Long id, String reason, Long actorId, Role role) {
        LeaveRequest l = leaves.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        Employee e = l.getEmployee();
        boolean owner = actorId.equals(e.getUserId());
        if (!owner && role != Role.ADMIN) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Request not found");
        }
        if (l.getStatus() != LeaveStatus.PENDING && l.getStatus() != LeaveStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request can no longer be cancelled");
        }
        if (l.getStatus() == LeaveStatus.APPROVED && !l.getFromDate().isAfter(HrDates.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Leave that has already started cannot be cancelled");
        }
        l.setStatus(LeaveStatus.CANCELLED);
        l.setDecisionNote(clean(reason));
        if (!owner) {
            notifications.toUser(e.getUserId(), "LEAVE_CANCELLED",
                    "Your " + nice(l.getLeaveType()) + " leave for " + l.getFromDate() + " to " + l.getToDate()
                            + " was cancelled by the administrator", "/my-work");
        }
        audit.log(actorId, "LEAVE_CANCELLED", "LEAVE", id, e.getEmployeeNo());
        return LeaveResponse.from(l);
    }

    // ---------- helpers ----------

    private LeaveResponse create(Employee e, LeaveType type, LocalDate from, LocalDate to, String reason,
                                 Long actorId, boolean direct) {
        LocalDate today = HrDates.today();
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        if (from.getYear() != to.getYear()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A request cannot run across two years. Please split it.");
        }
        if (from.plusDays(60).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A single request cannot be longer than 60 days");
        }
        if (from.isBefore(today.minusDays(direct ? 90 : 7))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, direct
                    ? "Leave can be recorded for up to 90 days back"
                    : "Leave can be requested for up to 7 days back");
        }
        int days = HrDates.workingDays(from, to);
        if (days == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "These dates contain no working days (Saturdays and Sundays are not counted)");
        }
        if (leaves.overlaps(e.getId(), List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), from, to, 0L)) {
            throw new ApiException(HttpStatus.CONFLICT, "These dates overlap another leave request");
        }
        Integer entitlement = type.getEntitlement();
        if (entitlement != null) {
            LocalDate ys = LocalDate.of(from.getYear(), 1, 1);
            LocalDate ye = LocalDate.of(from.getYear(), 12, 31);
            long used = leaves.sumDays(e.getId(), type, LeaveStatus.APPROVED, ys, ye)
                    + leaves.sumDays(e.getId(), type, LeaveStatus.PENDING, ys, ye);
            if (used + days > entitlement) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only " + Math.max(0, entitlement - used) + " day(s) of "
                        + nice(type) + " leave are left for " + from.getYear());
            }
        }

        User actor = user(actorId);
        LeaveRequest l = new LeaveRequest();
        l.setEmployee(e);
        l.setLeaveType(type);
        l.setFromDate(from);
        l.setToDate(to);
        l.setDays(days);
        l.setReason(clean(reason));
        l.setRequestedBy(actor);
        if (direct) {
            l.setStatus(LeaveStatus.APPROVED);
            l.setDecidedBy(actor);
            l.setDecidedAt(Instant.now());
            l.setDecisionNote("Recorded by the administrator");
        }
        leaves.save(l);

        if (direct) {
            notifications.toUser(e.getUserId(), "LEAVE_RECORDED",
                    days + " day(s) of " + nice(type) + " leave were recorded for you (" + from + " to " + to + ")", "/my-work");
        } else {
            notifications.toRole(Role.ADMIN, "LEAVE_REQUEST",
                    e.getFullName() + " requested " + days + " working day(s) of " + nice(type) + " leave", "/hr");
        }
        audit.log(actorId, direct ? "LEAVE_RECORDED" : "LEAVE_REQUESTED", "LEAVE", l.getId(),
                e.getEmployeeNo() + ", " + type + " " + from + " to " + to + ", " + days + " day(s)");
        return LeaveResponse.from(l);
    }

    private Employee lock(Long id) {
        return employees.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private String nice(LeaveType t) {
        return t.name().toLowerCase();
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
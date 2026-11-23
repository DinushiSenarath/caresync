package backend.hr;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.hr.HrDtos.*;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(HrDates.ZONE);

    private final AttendanceRecordRepository records;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final LeaveRequestRepository leaves;
    private final UserRepository users;
    private final AuditService audit;

    // ---------- self service ----------

    @Transactional
    public AttendanceResponse checkIn(Long userId) {
        Employee e = lock(employeeService.forUser(userId).getId());
        LocalDate today = HrDates.today();
        if (leaves.onLeave(e.getId(), LeaveStatus.APPROVED, today)) {
            throw new ApiException(HttpStatus.CONFLICT, "You are on approved leave today");
        }
        AttendanceRecord existing = records.findByEmployeeIdAndWorkDate(e.getId(), today).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == AttendanceStatus.ABSENT) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Your attendance for today was marked absent by an administrator. Please contact them.");
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "You already checked in today at " + CLOCK.format(existing.getCheckIn()));
        }
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(e);
        r.setWorkDate(today);
        r.setCheckIn(Instant.now());
        r.setStatus(AttendanceStatus.PRESENT);
        r.setSource(AttendanceSource.SELF);
        r.setRecordedBy(user(userId));
        saveChecked(r);
        audit.log(userId, "ATTENDANCE_CHECK_IN", "EMPLOYEE", e.getId(), null);
        return AttendanceResponse.from(r);
    }

    @Transactional
    public AttendanceResponse checkOut(Long userId) {
        Employee e = lock(employeeService.forUser(userId).getId());
        AttendanceRecord r = records.findByEmployeeIdAndWorkDate(e.getId(), HrDates.today())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "You have not checked in today"));
        if (r.getStatus() == AttendanceStatus.ABSENT || r.getCheckIn() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "You have not checked in today");
        }
        if (r.getCheckOut() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "You already checked out today at " + CLOCK.format(r.getCheckOut()));
        }
        r.setCheckOut(Instant.now());
        audit.log(userId, "ATTENDANCE_CHECK_OUT", "EMPLOYEE", e.getId(), null);
        return AttendanceResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> myMonth(Long userId, String month) {
        Employee e = employeeService.forUser(userId);
        YearMonth ym = HrDates.month(month);
        return records.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
                        e.getId(), ym.atDay(1), ym.atEndOfMonth())
                .stream().map(AttendanceResponse::from).toList();
    }

    // ---------- administrator ----------

    @Transactional(readOnly = true)
    public PagedResponse<AttendanceResponse> list(Long employeeId, LocalDate from, LocalDate to, int page, int size) {
        LocalDate today = HrDates.today();
        LocalDate f = from != null ? from : today.withDayOfMonth(1);
        LocalDate t = to != null ? to : today;
        if (t.isBefore(f)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The end date is before the start date");
        }
        if (f.plusDays(366).isBefore(t)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of one year or less");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(records.search(employeeId == null, employeeId == null ? 0L : employeeId, f, t, pageable)
                .map(AttendanceResponse::from));
    }

    @Transactional
    public AttendanceResponse create(ManualAttendanceRequest q, Long actorId) {
        if (q.employeeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an employee");
        }
        Employee e = lock(q.employeeId());
        if (records.findByEmployeeIdAndWorkDate(e.getId(), q.workDate()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A record already exists for this date. Edit it instead.");
        }
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(e);
        r.setWorkDate(q.workDate());
        applyManual(r, q, actorId);
        saveChecked(r);
        audit.log(actorId, "ATTENDANCE_RECORDED", "EMPLOYEE", e.getId(),
                q.workDate() + " " + q.status() + ", note=" + r.getNote());
        return AttendanceResponse.from(r);
    }

    @Transactional
    public AttendanceResponse update(Long id, ManualAttendanceRequest q, Long actorId) {
        AttendanceRecord r = records.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!q.workDate().equals(r.getWorkDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The date of a record cannot be changed. Create a new record instead.");
        }
        applyManual(r, q, actorId);
        saveChecked(r);
        audit.log(actorId, "ATTENDANCE_CORRECTED", "EMPLOYEE", r.getEmployee().getId(),
                q.workDate() + " " + q.status() + ", note=" + r.getNote());
        return AttendanceResponse.from(r);
    }

    @Transactional(readOnly = true)
    public List<SummaryRow> summary(String monthStr) {
        YearMonth ym = HrDates.month(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = HrDates.today();
        LocalDate upto = end.isAfter(today) ? today : end;
        int weekdaysElapsed = upto.isBefore(start) ? 0 : HrDates.workingDays(start, upto);

        Map<Long, List<AttendanceRecord>> byEmp = records.findByWorkDateBetween(start, end).stream()
                .collect(Collectors.groupingBy(r -> r.getEmployee().getId()));
        Map<Long, List<LeaveRequest>> leaveByEmp = leaves.findOverlapping(LeaveStatus.APPROVED, start, end).stream()
                .collect(Collectors.groupingBy(l -> l.getEmployee().getId()));

        List<SummaryRow> out = new ArrayList<>();
        for (Employee e : employees.findByActiveTrueOrderByFullNameAsc()) {
            double present = 0;
            int absent = 0;
            long minutes = 0;
            Set<LocalDate> covered = new HashSet<>();
            for (AttendanceRecord r : byEmp.getOrDefault(e.getId(), List.of())) {
                if (r.getStatus() == AttendanceStatus.PRESENT) present += 1;
                if (r.getStatus() == AttendanceStatus.HALF_DAY) present += 0.5;
                if (r.getStatus() == AttendanceStatus.ABSENT) absent++;
                if (r.getCheckIn() != null && r.getCheckOut() != null) {
                    minutes += Duration.between(r.getCheckIn(), r.getCheckOut()).toMinutes();
                }
                if (HrDates.isWorkingDay(r.getWorkDate()) && !r.getWorkDate().isAfter(upto)) covered.add(r.getWorkDate());
            }
            int leaveDays = 0;
            for (LeaveRequest l : leaveByEmp.getOrDefault(e.getId(), List.of())) {
                LocalDate a = l.getFromDate().isBefore(start) ? start : l.getFromDate();
                LocalDate b = l.getToDate().isAfter(end) ? end : l.getToDate();
                leaveDays += HrDates.workingDays(a, b);
                for (LocalDate d = a; !d.isAfter(b) && !d.isAfter(upto); d = d.plusDays(1)) {
                    if (HrDates.isWorkingDay(d)) covered.add(d);
                }
            }
            int without = Math.max(0, weekdaysElapsed - covered.size());
            out.add(new SummaryRow(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getDepartment().getName(),
                    present, absent, leaveDays, without, Math.round(minutes / 60.0 * 100.0) / 100.0));
        }
        return out;
    }

    // ---------- helpers ----------

    private void applyManual(AttendanceRecord r, ManualAttendanceRequest q, Long actorId) {
        if (q.workDate().isAfter(HrDates.today())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The date cannot be in the future");
        }
        if (q.status() == AttendanceStatus.ABSENT) {
            if (q.checkInTime() != null || q.checkOutTime() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "An absent day cannot have check-in or check-out times");
            }
            r.setCheckIn(null);
            r.setCheckOut(null);
        } else {
            if (q.checkInTime() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the check-in time");
            }
            Instant in = at(q.workDate(), q.checkInTime());
            Instant out = q.checkOutTime() == null ? null : at(q.workDate(), q.checkOutTime());
            if (out != null && !out.isAfter(in)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Check-out must be after check-in");
            }
            r.setCheckIn(in);
            r.setCheckOut(out);
        }
        r.setStatus(q.status());
        r.setSource(AttendanceSource.MANUAL);
        r.setNote(q.note().trim());
        r.setRecordedBy(user(actorId));
    }

    private Instant at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(HrDates.ZONE).toInstant();
    }

    private Employee lock(Long id) {
        return employees.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private void saveChecked(AttendanceRecord r) {
        try {
            records.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "A record for this date already exists");
        }
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
    }
}
package backend.hr;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class HrDtos {
    private HrDtos() {}

    private static final String PHONE = "^(\\+94|0)[0-9]{9}$";
    private static final String PHONE_MSG = "use a Sri Lankan number such as 0771234567 or +94771234567";

    // ----- employees -----
    public record EmployeeRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^([0-9]{9}[VvXx]|[0-9]{12})$", message = "invalid NIC format") String nic,
            @NotBlank @Pattern(regexp = PHONE, message = PHONE_MSG) String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 255) String address,
            @NotBlank @Size(max = 100) String jobTitle,
            @NotNull Long departmentId,
            @NotNull EmploymentType employmentType,
            @NotNull @PastOrPresent LocalDate hireDate,
            Long userId,
            @Size(max = 120) String emergencyContactName,
            @Pattern(regexp = "^$|" + PHONE, message = PHONE_MSG) String emergencyContactPhone,
            Boolean active) {}

    public record EmployeeResponse(Long id, String employeeNo, String fullName, String nic, String phone,
                                   String email, String address, String jobTitle, Long departmentId,
                                   String departmentName, EmploymentType employmentType, LocalDate hireDate,
                                   Long userId, String username, String userRole, String emergencyContactName,
                                   String emergencyContactPhone, boolean active) {}

    public record UnlinkedUser(Long id, String username, String fullName, String role) {}

    // ----- attendance -----
    public record ManualAttendanceRequest(
            Long employeeId,
            @NotNull LocalDate workDate,
            @NotNull AttendanceStatus status,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            @NotBlank @Size(max = 255) String note) {}

    public record AttendanceResponse(Long id, Long employeeId, String employeeNo, String employeeName,
                                     LocalDate workDate, Instant checkIn, Instant checkOut, Double hours,
                                     AttendanceStatus status, AttendanceSource source, String note,
                                     String recordedByName) {
        public static AttendanceResponse from(AttendanceRecord r) {
            Double hours = null;
            if (r.getCheckIn() != null && r.getCheckOut() != null) {
                hours = BigDecimal.valueOf(Duration.between(r.getCheckIn(), r.getCheckOut()).toMinutes() / 60.0)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }
            return new AttendanceResponse(r.getId(), r.getEmployee().getId(), r.getEmployee().getEmployeeNo(),
                    r.getEmployee().getFullName(), r.getWorkDate(), r.getCheckIn(), r.getCheckOut(), hours,
                    r.getStatus(), r.getSource(), r.getNote(), r.getRecordedBy().getFullName());
        }
    }

    public record SummaryRow(Long employeeId, String employeeNo, String fullName, String departmentName,
                             double presentDays, int absentDays, int leaveDays, int daysWithoutRecord,
                             double hoursWorked) {}

    // ----- leave -----
    public record LeaveBody(
            @NotNull LeaveType leaveType,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @Size(max = 255) String reason) {}

    public record RecordLeaveBody(
            @NotNull Long employeeId,
            @NotNull LeaveType leaveType,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotBlank @Size(max = 255) String reason) {}

    public record DecisionBody(@NotNull Boolean approve, @Size(max = 255) String note) {}

    public record CancelBody(@Size(max = 255) String reason) {}

    public record LeaveResponse(Long id, Long employeeId, String employeeNo, String employeeName,
                                LeaveType leaveType, LocalDate fromDate, LocalDate toDate, int days, String reason,
                                LeaveStatus status, String decidedByName, String decisionNote, Instant decidedAt,
                                Instant createdAt) {
        public static LeaveResponse from(LeaveRequest l) {
            return new LeaveResponse(l.getId(), l.getEmployee().getId(), l.getEmployee().getEmployeeNo(),
                    l.getEmployee().getFullName(), l.getLeaveType(), l.getFromDate(), l.getToDate(), l.getDays(),
                    l.getReason(), l.getStatus(), l.getDecidedBy() == null ? null : l.getDecidedBy().getFullName(),
                    l.getDecisionNote(), l.getDecidedAt(), l.getCreatedAt());
        }
    }

    public record LeaveBalance(LeaveType type, Integer entitlement, int used, int pending, Integer remaining) {}

    public record MeResponse(Long employeeId, String employeeNo, String fullName, String jobTitle,
                             String departmentName, LocalDate hireDate, boolean onLeaveToday,
                             AttendanceResponse today, List<LeaveBalance> balances) {}
}
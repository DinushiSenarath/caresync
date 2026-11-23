package backend.hr;

import backend.common.PagedResponse;
import backend.hr.HrDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {

    private static final String STAFF =
            "hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','WARD_STAFF')";

    private final HrSelfService self;
    private final AttendanceService attendance;
    private final LeaveService leave;

    // ----- the signed-in staff member -----

    @GetMapping("/me")
    @PreAuthorize(STAFF)
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return self.me(JwtUtil.userId(jwt));
    }

    @PostMapping("/me/check-in")
    @PreAuthorize(STAFF)
    public AttendanceResponse checkIn(@AuthenticationPrincipal Jwt jwt) {
        return attendance.checkIn(JwtUtil.userId(jwt));
    }

    @PostMapping("/me/check-out")
    @PreAuthorize(STAFF)
    public AttendanceResponse checkOut(@AuthenticationPrincipal Jwt jwt) {
        return attendance.checkOut(JwtUtil.userId(jwt));
    }

    @GetMapping("/me/attendance")
    @PreAuthorize(STAFF)
    public List<AttendanceResponse> myAttendance(@RequestParam(required = false) String month,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return attendance.myMonth(JwtUtil.userId(jwt), month);
    }

    @GetMapping("/me/leave")
    @PreAuthorize(STAFF)
    public PagedResponse<LeaveResponse> myLeave(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        return leave.mine(JwtUtil.userId(jwt), page, size);
    }

    @PostMapping("/me/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF)
    public LeaveResponse requestLeave(@Valid @RequestBody LeaveBody body, @AuthenticationPrincipal Jwt jwt) {
        return leave.request(JwtUtil.userId(jwt), body);
    }

    @PostMapping("/leave/{id}/cancel")
    @PreAuthorize(STAFF)
    public LeaveResponse cancelLeave(@PathVariable Long id, @Valid @RequestBody(required = false) CancelBody body,
                                     @AuthenticationPrincipal Jwt jwt) {
        return leave.cancel(id, body == null ? null : body.reason(), JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    // ----- administrator -----

    @GetMapping("/attendance")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<AttendanceResponse> attendanceList(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return attendance.list(employeeId, from, to, page, size);
    }

    @GetMapping("/attendance/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SummaryRow> summary(@RequestParam(required = false) String month) {
        return attendance.summary(month);
    }

    @PostMapping("/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AttendanceResponse createAttendance(@Valid @RequestBody ManualAttendanceRequest body,
                                               @AuthenticationPrincipal Jwt jwt) {
        return attendance.create(body, JwtUtil.userId(jwt));
    }

    @PutMapping("/attendance/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AttendanceResponse updateAttendance(@PathVariable Long id, @Valid @RequestBody ManualAttendanceRequest body,
                                               @AuthenticationPrincipal Jwt jwt) {
        return attendance.update(id, body, JwtUtil.userId(jwt));
    }

    @GetMapping("/leave")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<LeaveResponse> leaveList(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long employeeId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return leave.list(status, employeeId, page, size);
    }

    @PostMapping("/leave/record")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LeaveResponse recordLeave(@Valid @RequestBody RecordLeaveBody body, @AuthenticationPrincipal Jwt jwt) {
        return leave.record(body, JwtUtil.userId(jwt));
    }

    @PostMapping("/leave/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public LeaveResponse decide(@PathVariable Long id, @Valid @RequestBody DecisionBody body,
                                @AuthenticationPrincipal Jwt jwt) {
        return leave.decide(id, body, JwtUtil.userId(jwt));
    }
}
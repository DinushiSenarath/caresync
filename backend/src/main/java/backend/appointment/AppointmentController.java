package backend.appointment;

import backend.appointment.AppointmentDtos.*;
import backend.common.PagedResponse;
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
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private static final String STAFF = "hasAnyRole('ADMIN','RECEPTIONIST')";
    private static final String CLINIC = "hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR','NURSE')";

    private final AppointmentService service;

    @GetMapping
    @PreAuthorize(CLINIC)
    public PagedResponse<AppointmentResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.search(date, from, to, doctorId, status, patientId, page, size,
                JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @GetMapping("/slots")
    @PreAuthorize(STAFF)
    public List<SlotResponse> slots(@RequestParam Long doctorId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(doctorId, date);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CLINIC)
    public AppointmentResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF)
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.book(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(STAFF)
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        return service.reschedule(id, request, JwtUtil.userId(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR')")
    public AppointmentResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, request, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }
}
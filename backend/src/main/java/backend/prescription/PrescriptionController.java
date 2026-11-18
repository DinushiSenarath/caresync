package backend.prescription;

import backend.prescription.PrescriptionDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService service;

    @PostMapping("/api/appointments/{appointmentId}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse create(@PathVariable Long appointmentId,
                                       @Valid @RequestBody PrescriptionRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.create(appointmentId, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/appointments/{appointmentId}/prescriptions")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public List<PrescriptionResponse> forAppointment(@PathVariable Long appointmentId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return service.listForAppointment(appointmentId, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @GetMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE','PHARMACIST')")
    public PrescriptionResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PutMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse update(@PathVariable Long id, @Valid @RequestBody PrescriptionRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/prescriptions/{id}/issue")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse issue(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.issue(id, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/prescriptions/{id}/cancel")
    @PreAuthorize("hasRole('DOCTOR')")
    public PrescriptionResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, request.reason(), JwtUtil.userId(jwt));
    }
}
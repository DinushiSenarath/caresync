package backend.clinical;

import backend.clinical.ClinicalDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments/{appointmentId}/note")
@RequiredArgsConstructor
public class ConsultationNoteController {

    private final ConsultationNoteService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public NoteResponse get(@PathVariable Long appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.get(appointmentId, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PutMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse save(@PathVariable Long appointmentId, @Valid @RequestBody NoteRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.save(appointmentId, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/finalize")
    @PreAuthorize("hasRole('DOCTOR')")
    public NoteResponse finalise(@PathVariable Long appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.finalise(appointmentId, JwtUtil.userId(jwt));
    }
}
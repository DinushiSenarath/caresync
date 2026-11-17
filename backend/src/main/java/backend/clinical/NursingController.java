package backend.clinical;

import backend.clinical.ClinicalDtos.*;
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
@RequestMapping("/api/appointments/{appointmentId}")
@RequiredArgsConstructor
public class NursingController {

    private final NursingService service;

    @GetMapping("/vitals")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public List<VitalResponse> vitals(@PathVariable Long appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.listVitals(appointmentId, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/vitals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public VitalResponse recordVitals(@PathVariable Long appointmentId, @Valid @RequestBody VitalRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.recordVitals(appointmentId, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/nursing-notes")
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public List<NursingNoteResponse> notes(@PathVariable Long appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.listNotes(appointmentId, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/nursing-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('NURSE')")
    public NursingNoteResponse addNote(@PathVariable Long appointmentId,
                                       @Valid @RequestBody NursingNoteRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.addNote(appointmentId, request, JwtUtil.userId(jwt));
    }
}
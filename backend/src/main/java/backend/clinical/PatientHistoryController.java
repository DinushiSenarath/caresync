package backend.clinical;

import backend.clinical.ClinicalDtos.HistoryResponse;
import backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients/{patientId}/history")
@RequiredArgsConstructor
public class PatientHistoryController {

    private final PatientHistoryService service;

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public HistoryResponse history(@PathVariable Long patientId, @AuthenticationPrincipal Jwt jwt) {
        return service.history(patientId, JwtUtil.userId(jwt));
    }
}
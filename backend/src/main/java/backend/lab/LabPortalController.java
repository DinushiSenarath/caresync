package backend.lab;

import backend.lab.LabDtos.RequestResponse;
import backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LabPortalController {

    private final LabService service;

    @GetMapping("/api/portal/lab-results")
    @PreAuthorize("hasRole('PATIENT')")
    public List<RequestResponse> results(@AuthenticationPrincipal Jwt jwt) {
        return service.portalResults(JwtUtil.userId(jwt));
    }
}
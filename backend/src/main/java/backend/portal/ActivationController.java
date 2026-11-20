package backend.portal;

import backend.portal.PortalDtos.ActivateRequest;
import backend.portal.PortalDtos.ActivationResponse;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ActivationController {

    private final ActivationService service;

    @PostMapping("/api/patients/{patientId}/activation-code")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ActivationService.IssuedCode issue(@PathVariable Long patientId, @AuthenticationPrincipal Jwt jwt) {
        return service.issue(patientId, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/auth/activate")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivationResponse activate(@Valid @RequestBody ActivateRequest request) {
        service.activate(request);
        return new ActivationResponse("Account created. You can now sign in with your username and password.");
    }
}
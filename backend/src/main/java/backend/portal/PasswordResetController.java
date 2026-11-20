package backend.portal;

import backend.common.ApiException;
import backend.portal.ResetDtos.*;
import backend.registration.IpRateLimiter;
import backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService service;
    private final IpRateLimiter limiter;

    @Value("${app.reset.max-per-ip-per-hour:10}")
    private int maxPerHour;

    @PostMapping("/api/patients/{patientId}/password-reset-code")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
    public ActivationService.IssuedCode issue(@PathVariable Long patientId,
                                              @Valid @RequestBody IssueResetRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return service.issue(patientId, request.verification(), JwtUtil.userId(jwt));
    }

    @PostMapping("/api/auth/reset-password")
    public ResetResponse reset(@Valid @RequestBody PatientResetRequest request, HttpServletRequest http) {
        if (!limiter.allow("reset:" + http.getRemoteAddr(), maxPerHour, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }
        service.reset(request);
        return new ResetResponse("Your password has been changed. You can now sign in with your new password.");
    }
}
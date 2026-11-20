package backend.registration;

import backend.common.ApiException;
import backend.registration.RegistrationDtos.PublicRegistrationRequest;
import backend.registration.RegistrationDtos.PublicRegistrationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class PublicRegistrationController {

    private final RegistrationService service;
    private final IpRateLimiter limiter;

    @Value("${app.registration.max-per-ip-per-hour:10}")
    private int maxPerHour;

    @PostMapping("/api/public/registration-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicRegistrationResponse submit(@Valid @RequestBody PublicRegistrationRequest request,
                                             HttpServletRequest http) {
        if (!limiter.allow("registration:" + http.getRemoteAddr(), maxPerHour, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }
        return new PublicRegistrationResponse(service.submit(request));
    }
}
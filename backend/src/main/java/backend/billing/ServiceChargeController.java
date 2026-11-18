package backend.billing;

import backend.billing.BillingDtos.*;
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
@RequestMapping("/api/service-charges")
@RequiredArgsConstructor
public class ServiceChargeController {

    private final ServiceChargeService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public List<ServiceChargeResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceChargeResponse create(@Valid @RequestBody ServiceChargeRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ServiceChargeResponse update(@PathVariable Long id, @Valid @RequestBody ServiceChargeRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, JwtUtil.userId(jwt));
    }
}
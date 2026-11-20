package backend.registration;

import backend.common.PagedResponse;
import backend.registration.RegistrationDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registration-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST')")
public class RegistrationAdminController {

    private final RegistrationService service;

    @GetMapping
    public PagedResponse<RegistrationResponse> list(@RequestParam(required = false) RegistrationStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}")
    public RegistrationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    public ApprovalResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request,
                                    @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.approve(id, request, confirmDuplicate, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/{id}/reject")
    public RegistrationResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.reject(id, request.note(), JwtUtil.userId(jwt));
    }
}
package backend.ward;

import backend.common.PagedResponse;
import backend.security.JwtUtil;
import backend.ward.WardDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admissions")
@RequiredArgsConstructor
public class AdmissionController {

    private static final String VIEW = "hasAnyRole('ADMIN','WARD_STAFF','DOCTOR','NURSE')";

    private final AdmissionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WARD_STAFF')")
    public AdmissionResponse admit(@Valid @RequestBody AdmissionRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.admit(request, JwtUtil.userId(jwt));
    }

    @GetMapping
    @PreAuthorize(VIEW)
    public PagedResponse<AdmissionSummary> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        return service.list(q, status, page, size, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public AdmissionResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('WARD_STAFF')")
    public AdmissionResponse transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.transfer(id, request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}/discharge-summary")
    @PreAuthorize("hasRole('DOCTOR')")
    public AdmissionResponse summary(@PathVariable Long id, @Valid @RequestBody SummaryRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.saveSummary(id, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/{id}/discharge")
    @PreAuthorize("hasAnyRole('WARD_STAFF','DOCTOR')")
    public AdmissionResponse discharge(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.discharge(id, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('WARD_STAFF')")
    public AdmissionResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, request.reason(), JwtUtil.userId(jwt));
    }
}
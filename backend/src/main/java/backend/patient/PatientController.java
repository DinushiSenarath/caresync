package backend.patient;

import backend.common.ActiveRequest;
import backend.common.PagedResponse;
import backend.patient.PatientDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private static final String VIEW =
            "hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','WARD_STAFF')";
    private static final String WRITE = "hasAnyRole('ADMIN','RECEPTIONIST')";

    private final PatientService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public PagedResponse<PatientResponse> search(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "false") boolean includeInactive,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return service.search(q, includeInactive, page, size, JwtUtil.role(jwt));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public PatientResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, JwtUtil.role(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    public PatientResponse create(@Valid @RequestBody PatientRequest request,
                                  @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, confirmDuplicate, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request,
                                  @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, confirmDuplicate, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public PatientResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.setActive(id, request.active(), JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }
}
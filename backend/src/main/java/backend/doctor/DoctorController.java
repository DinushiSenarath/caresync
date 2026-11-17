package backend.doctor;

import backend.doctor.DoctorDtos.*;
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
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService service;

    @GetMapping
    public List<DoctorResponse> list(@RequestParam(required = false) Long departmentId,
                                     @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(departmentId, activeOnly);
    }

    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse create(@Valid @RequestBody CreateDoctorRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, JwtUtil.userId(jwt));
    }
}
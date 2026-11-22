package backend.ward;

import backend.security.JwtUtil;
import backend.user.Role;
import backend.ward.WardDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WardController {

    private final WardService service;

    @GetMapping("/wards")
    @PreAuthorize("hasAnyRole('ADMIN','WARD_STAFF','DOCTOR','NURSE')")
    public List<WardResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(JwtUtil.role(jwt) == Role.ADMIN);
    }

    @PostMapping("/wards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WardResponse create(@Valid @RequestBody WardRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.createWard(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/wards/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public WardResponse update(@PathVariable Long id, @Valid @RequestBody WardRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return service.updateWard(id, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/wards/{wardId}/beds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BedResponse createBed(@PathVariable Long wardId, @Valid @RequestBody BedRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.createBed(wardId, request, JwtUtil.userId(jwt));
    }

    @PutMapping("/beds/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BedResponse updateBed(@PathVariable Long id, @Valid @RequestBody BedRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.updateBed(id, request, JwtUtil.userId(jwt));
    }
}
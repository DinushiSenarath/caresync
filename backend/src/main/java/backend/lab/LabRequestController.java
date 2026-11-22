package backend.lab;

import backend.common.PagedResponse;
import backend.lab.LabDtos.*;
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
@RequiredArgsConstructor
public class LabRequestController {

    private final LabService service;

    @PostMapping("/api/lab/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    public RequestResponse create(@Valid @RequestBody LabRequestCreate request, @AuthenticationPrincipal Jwt jwt) {
        return service.createRequest(request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/appointments/{appointmentId}/lab-requests")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<RequestResponse> forAppointment(@PathVariable Long appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return service.forAppointment(appointmentId, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/lab/requests")
    @PreAuthorize("hasRole('LAB_TECH')")
    public PagedResponse<RequestSummary> worklist(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.worklist(q, status, page, size);
    }

    @GetMapping("/api/lab/requests/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECH')")
    public RequestResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }

    @PostMapping("/api/lab/requests/{id}/collect")
    @PreAuthorize("hasRole('LAB_TECH')")
    public RequestResponse collect(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) CollectRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.collect(id, request == null ? null : request.sampleNo(), JwtUtil.userId(jwt));
    }

    @PutMapping("/api/lab/requests/{id}/results")
    @PreAuthorize("hasRole('LAB_TECH')")
    public RequestResponse results(@PathVariable Long id, @Valid @RequestBody ResultsRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.saveResults(id, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/lab/requests/{id}/verify")
    @PreAuthorize("hasRole('LAB_TECH')")
    public RequestResponse verify(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.verify(id, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/lab/requests/{id}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECH')")
    public RequestResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(id, request.reason(), JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }
}
package backend.prescription;

import backend.common.PagedResponse;
import backend.prescription.PrescriptionDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PHARMACIST')")
public class DispensingController {

    private final DispensingService service;

    @GetMapping("/api/pharmacy/queue")
    public PagedResponse<QueueEntry> queue(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.queue(page, size);
    }

    @PostMapping("/api/prescriptions/{id}/dispense")
    public PrescriptionResponse dispense(@PathVariable Long id, @Valid @RequestBody DispenseRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.dispense(id, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/prescriptions/{id}/dispensing")
    public List<DispenseRecord> history(@PathVariable Long id) {
        return service.history(id);
    }
}
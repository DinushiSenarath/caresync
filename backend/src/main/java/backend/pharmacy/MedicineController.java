package backend.pharmacy;

import backend.common.PagedResponse;
import backend.pharmacy.PharmacyDtos.*;
import backend.security.JwtUtil;
import backend.user.Role;
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
public class MedicineController {

    private final PharmacyService service;

    @GetMapping("/api/medicines")
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST','DOCTOR')")
    public PagedResponse<MedicineResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "false") boolean includeInactive,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        boolean inactiveAllowed = includeInactive && JwtUtil.role(jwt) != Role.DOCTOR;
        return service.listMedicines(q, inactiveAllowed, page, size);
    }

    @PostMapping("/api/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PHARMACIST')")
    public MedicineResponse create(@Valid @RequestBody MedicineRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.createMedicine(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/api/medicines/{id}")
    @PreAuthorize("hasRole('PHARMACIST')")
    public MedicineResponse update(@PathVariable Long id, @Valid @RequestBody MedicineRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.updateMedicine(id, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/medicines/{id}/batches")
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    public List<BatchResponse> batches(@PathVariable Long id) {
        return service.listBatches(id);
    }

    @PostMapping("/api/medicines/{id}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PHARMACIST')")
    public BatchResponse addBatch(@PathVariable Long id, @Valid @RequestBody BatchRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.addBatch(id, request, JwtUtil.userId(jwt));
    }

    @PostMapping("/api/batches/{batchId}/adjust")
    @PreAuthorize("hasRole('PHARMACIST')")
    public BatchResponse adjust(@PathVariable Long batchId, @Valid @RequestBody AdjustRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.adjust(batchId, request, JwtUtil.userId(jwt));
    }
}
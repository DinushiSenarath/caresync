package backend.pharmacy;

import backend.common.PagedResponse;
import backend.pharmacy.PharmacyDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService service;

    @GetMapping("/pharmacy/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    public AlertsResponse alerts() {
        return service.alerts();
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    public PagedResponse<MovementResponse> movements(@RequestParam(required = false) Long medicineId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return service.listMovements(medicineId, page, size);
    }
}
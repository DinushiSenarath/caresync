package backend.billing;

import backend.billing.AdjustmentDtos.*;
import backend.common.PagedResponse;
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
public class AdjustmentController {

    private static final String VIEW = "hasAnyRole('ADMIN','CASHIER')";

    private final AdjustmentService service;

    @PostMapping("/api/invoices/{invoiceId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CASHIER')")
    public AdjustmentResponse request(@PathVariable Long invoiceId, @Valid @RequestBody AdjustmentRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.request(invoiceId, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/invoices/{invoiceId}/adjustments")
    @PreAuthorize(VIEW)
    public List<AdjustmentResponse> forInvoice(@PathVariable Long invoiceId) {
        return service.forInvoice(invoiceId);
    }

    @GetMapping("/api/adjustments")
    @PreAuthorize(VIEW)
    public PagedResponse<AdjustmentResponse> list(@RequestParam(required = false) AdjustmentStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @PostMapping("/api/adjustments/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public AdjustmentResponse decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.decide(id, request, JwtUtil.userId(jwt));
    }
}
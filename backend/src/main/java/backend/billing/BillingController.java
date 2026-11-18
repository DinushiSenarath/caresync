package backend.billing;

import backend.billing.BillingDtos.*;
import backend.common.PagedResponse;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BillingController {

    private static final String VIEW = "hasAnyRole('ADMIN','CASHIER')";

    private final BillingService service;

    @PostMapping("/api/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceDetail create(@Valid @RequestBody InvoiceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.createInvoice(request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/invoices")
    @PreAuthorize(VIEW)
    public PagedResponse<InvoiceSummary> list(@RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(required = false) Long patientId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(status, patientId, page, size);
    }

    @GetMapping("/api/invoices/{id}")
    @PreAuthorize(VIEW)
    public InvoiceDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/invoices/{id}/payments")
    @PreAuthorize("hasRole('CASHIER')")
    public InvoiceDetail pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.pay(id, request, JwtUtil.userId(jwt));
    }

    @GetMapping("/api/payments/{id}/receipt")
    @PreAuthorize(VIEW)
    public ReceiptResponse receipt(@PathVariable Long id) {
        return service.receipt(id);
    }
}
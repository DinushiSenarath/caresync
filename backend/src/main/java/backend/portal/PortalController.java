package backend.portal;

import backend.appointment.AppointmentDtos.AppointmentResponse;
import backend.appointment.AppointmentDtos.SlotResponse;
import backend.billing.BillingDtos.InvoiceDetail;
import backend.billing.BillingDtos.InvoiceSummary;
import backend.billing.BillingDtos.ReceiptResponse;
import backend.common.PagedResponse;
import backend.portal.PortalDtos.*;
import backend.prescription.PrescriptionDtos.PrescriptionResponse;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PortalController {

    private final PortalService service;

    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return service.home(JwtUtil.userId(jwt));
    }

    @GetMapping("/me")
    public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(JwtUtil.userId(jwt));
    }

    @PutMapping("/me")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return service.updateProfile(JwtUtil.userId(jwt), request);
    }

    @GetMapping("/appointments")
    public PagedResponse<AppointmentResponse> appointments(@RequestParam(defaultValue = "upcoming") String scope,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return service.appointments(JwtUtil.userId(jwt), scope, page, size);
    }

    @GetMapping("/doctors")
    public List<DoctorOption> doctors() {
        return service.doctors();
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<SlotResponse> slots(@PathVariable Long doctorId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.slots(doctorId, date);
    }

    @PostMapping("/appointment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse request(@Valid @RequestBody AppointmentRequestBody request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.requestAppointment(JwtUtil.userId(jwt), request);
    }

    @PostMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) CancelAppointmentRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.cancelAppointment(JwtUtil.userId(jwt), id, request == null ? null : request.reason());
    }

    @GetMapping("/prescriptions")
    public List<PrescriptionResponse> prescriptions(@AuthenticationPrincipal Jwt jwt) {
        return service.prescriptions(JwtUtil.userId(jwt));
    }

    @GetMapping("/invoices")
    public PagedResponse<InvoiceSummary> invoices(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return service.invoices(JwtUtil.userId(jwt), page, size);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDetail invoice(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.invoice(JwtUtil.userId(jwt), id);
    }

    @GetMapping("/payments/{id}/receipt")
    public ReceiptResponse receipt(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.receipt(JwtUtil.userId(jwt), id);
    }
}
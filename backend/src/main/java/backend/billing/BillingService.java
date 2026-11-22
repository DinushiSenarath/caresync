package backend.billing;

import backend.appointment.Appointment;
import backend.appointment.AppointmentRepository;
import backend.appointment.AppointmentStatus;
import backend.audit.AuditService;
import backend.billing.BillingDtos.*;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.pharmacy.Medicine;
import backend.prescription.Prescription;
import backend.prescription.PrescriptionItem;
import backend.prescription.PrescriptionRepository;
import backend.prescription.PrescriptionStatus;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.lab.LabRequest;
import backend.lab.LabRequestItem;
import backend.lab.LabRequestRepository;
import backend.lab.LabStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Set<AppointmentStatus> BILLABLE =
            EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ServiceChargeRepository charges;
    private final AppointmentRepository appointments;
    private final PrescriptionRepository prescriptions;
    private final UserRepository users;
    private final AuditService audit;
    private final LabRequestRepository labRequests;

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceSummary> list(InvoiceStatus status, Long patientId, int page, int size) {
        List<InvoiceStatus> statuses = status != null ? List.of(status) : List.of(InvoiceStatus.values());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(invoices
                .search(statuses, patientId == null, patientId == null ? 0L : patientId, pageable)
                .map(InvoiceSummary::from));
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(Long id) {
        return detail(find(id));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Long paymentId) {
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Receipt not found"));
        Invoice i = find(p.getInvoiceId());
        return new ReceiptResponse(p.getReceiptNo(), i.getInvoiceNo(), i.getPatient().getPatientNo(),
                i.getPatient().getFullName(), p.getAmount(), p.getMethod(), p.getReference(),
                p.getBalanceAfter(), p.getCashier().getFullName(), p.getPaidAt(), p.getAmount().signum() < 0);
    }

    // ---------- writing ----------

    @Transactional
    public InvoiceDetail createInvoice(InvoiceRequest r, Long actorId) {
        Appointment a = appointments.findById(r.appointmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!BILLABLE.contains(a.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "An invoice can be created once the patient has checked in (status is " + a.getStatus() + ")");
        }
        if (invoices.existsByAppointmentIdAndStatusNot(a.getId(), InvoiceStatus.VOIDED)) {
            throw new ApiException(HttpStatus.CONFLICT, "This appointment already has an active invoice");
        }

        Invoice inv = new Invoice();
        inv.setInvoiceNo(String.format("INV-%06d", invoices.nextNumber()));
        inv.setPatient(a.getPatient());
        inv.setAppointmentId(a.getId());
        inv.setCreatedBy(actorId);

        Set<Long> seen = new HashSet<>();
        if (r.services() != null) {
            for (ServiceLine line : r.services()) {
                if (!seen.add(line.serviceChargeId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "The same service is listed twice");
                }
                ServiceCharge c = charges.findById(line.serviceChargeId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                                "Service " + line.serviceChargeId() + " not found"));
                if (!c.isActive()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, c.getName() + " is not active");
                }
                addItem(inv, "SERVICE", c.getName(), line.quantity(), c.getPrice());
            }
        }

        if (Boolean.TRUE.equals(r.includeDispensedMedicines())) {
            for (Prescription p : prescriptions.findByAppointmentIdOrderByIdAsc(a.getId())) {
                if (p.getStatus() == PrescriptionStatus.DRAFT || p.getStatus() == PrescriptionStatus.CANCELLED) continue;
                for (PrescriptionItem pi : p.getItems()) {
                    if (pi.getQtyDispensed() <= 0) continue;
                    Medicine m = pi.getMedicine();
                    if (m.getUnitPrice() == null || m.getUnitPrice().signum() <= 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST,
                                "Set a price for " + m.getName() + " before billing it");
                    }
                    String description = m.getName() + (m.getStrength() == null ? "" : " " + m.getStrength());
                    addItem(inv, "MEDICINE", description, pi.getQtyDispensed(), m.getUnitPrice());
                }
            }
        }

        if (Boolean.TRUE.equals(r.includeLabTests())) {
            for (LabRequest lr : labRequests.findByAppointmentIdOrderByIdAsc(a.getId())) {
                if (lr.getStatus() != LabStatus.COLLECTED && lr.getStatus() != LabStatus.VERIFIED) continue;
                for (LabRequestItem li : lr.getItems()) {
                    if (li.getPrice() == null || li.getPrice().signum() <= 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, li.getTestName() + " has no price set");
                    }
                    addItem(inv, "LAB", li.getTestName(), 1, li.getPrice());
                }
            }
        }

        if (inv.getItems().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add at least one service or dispensed medicine");
        }
        BigDecimal subtotal = inv.getItems().stream().map(InvoiceItem::getLineTotal)
                .reduce(ZERO, BigDecimal::add);
        if (subtotal.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The invoice total must be more than zero");
        }
        inv.setSubtotal(subtotal);
        inv.setDiscount(ZERO);
        inv.setTotal(subtotal);
        inv.setAmountPaid(ZERO);
        inv.setStatus(InvoiceStatus.UNPAID);

        try {
            invoices.saveAndFlush(inv);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This appointment has just been invoiced by someone else");
        }
        audit.log(actorId, "INVOICE_CREATED", "INVOICE", inv.getId(),
                "invoiceNo=" + inv.getInvoiceNo() + ", total=" + inv.getTotal());
        return detail(inv);
    }

    @Transactional
    public InvoiceDetail pay(Long invoiceId, PaymentRequest r, Long actorId) {
        Invoice inv = invoices.lockById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (inv.getStatus() == InvoiceStatus.VOIDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This invoice is voided");
        }
        BigDecimal amount = r.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = inv.balance();
        if (balance.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice is already fully paid");
        }
        if (amount.compareTo(balance) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "The amount is more than the outstanding balance of " + balance);
        }
        User cashier = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));

        inv.setAmountPaid(inv.getAmountPaid().add(amount));
        inv.recalculate();

        Payment p = new Payment();
        p.setReceiptNo(String.format("RC-%06d", payments.nextReceiptNumber()));
        p.setInvoiceId(inv.getId());
        p.setAmount(amount);
        p.setMethod(r.method());
        p.setReference(r.reference() == null || r.reference().isBlank() ? null : r.reference().trim());
        p.setBalanceAfter(inv.balance());
        p.setCashier(cashier);
        payments.save(p);

        audit.log(actorId, "PAYMENT_RECORDED", "INVOICE", inv.getId(),
                "receipt=" + p.getReceiptNo() + ", amount=" + amount + ", method=" + r.method());
        return detail(inv);
    }

    // ---------- helpers ----------

    private void addItem(Invoice inv, String type, String description, int quantity, BigDecimal unitPrice) {
        InvoiceItem item = new InvoiceItem();
        item.setInvoice(inv);
        item.setItemType(type);
        item.setDescription(description);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
        item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        inv.getItems().add(item);
    }

    private Invoice find(Long id) {
        return invoices.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private InvoiceDetail detail(Invoice i) {
        List<ItemResponse> items = i.getItems().stream().map(ItemResponse::from).toList();
        List<PaymentResponse> pays = payments.findByInvoiceIdOrderByPaidAtAscIdAsc(i.getId()).stream()
                .map(PaymentResponse::from).toList();
        return new InvoiceDetail(i.getId(), i.getInvoiceNo(), i.getAppointmentId(), i.getPatient().getId(),
                i.getPatient().getPatientNo(), i.getPatient().getFullName(), i.getSubtotal(), i.getDiscount(),
                i.getTotal(), i.getAmountPaid(), i.balance(), i.getStatus(), i.getVoidReason(), i.getCreatedAt(),
                items, pays);
    }
}
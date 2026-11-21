package backend.billing;

import backend.audit.AuditService;
import backend.billing.AdjustmentDtos.*;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import backend.notification.NotificationService;
import backend.user.Role;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdjustmentService {

    private final InvoiceAdjustmentRepository adjustments;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<AdjustmentResponse> forInvoice(Long invoiceId) {
        if (!invoices.existsById(invoiceId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        return adjustments.findByInvoiceIdOrderByIdDesc(invoiceId).stream().map(AdjustmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdjustmentResponse> list(AdjustmentStatus status, int page, int size) {
        List<AdjustmentStatus> statuses = status != null ? List.of(status) : List.of(AdjustmentStatus.values());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PagedResponse.of(adjustments.search(statuses, pageable).map(AdjustmentResponse::from));
    }

    // ---------- requesting ----------

    @Transactional
    public AdjustmentResponse request(Long invoiceId, AdjustmentRequest r, Long actorId) {
        Invoice inv = invoices.lockById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (adjustments.existsByInvoiceIdAndStatus(invoiceId, AdjustmentStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice already has a request waiting for approval");
        }

        BigDecimal amount;
        PaymentMethod refundMethod = null;
        if (r.type() == AdjustmentType.VOID) {
            if (r.amount() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A void does not take an amount");
            }
            amount = BigDecimal.ZERO.setScale(2);
        } else {
            if (r.amount() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "An amount is required");
            }
            amount = r.amount().setScale(2, RoundingMode.HALF_UP);
            if (r.type() == AdjustmentType.REFUND) {
                if (r.refundMethod() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Choose how the refund will be paid");
                }
                refundMethod = r.refundMethod();
            }
        }
        check(inv, r.type(), amount, HttpStatus.BAD_REQUEST);

        User cashier = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
        InvoiceAdjustment adj = new InvoiceAdjustment();
        adj.setInvoice(inv);
        adj.setType(r.type());
        adj.setAmount(amount);
        adj.setRefundMethod(refundMethod);
        adj.setReason(r.reason().trim());
        adj.setRequestedBy(cashier);
        adjustments.save(adj);

        audit.log(actorId, "ADJUSTMENT_REQUESTED", "INVOICE", invoiceId,
                r.type() + " " + amount + ", reason=" + adj.getReason());
        notifications.toRole(Role.ADMIN, "ADJUSTMENT_REQUESTED",
                "A " + r.type().name().toLowerCase() + " request for invoice " + inv.getInvoiceNo()
                        + " needs your decision", "/billing/adjustments");
        return AdjustmentResponse.from(adj);
    }

    // ---------- deciding ----------

    @Transactional
    public AdjustmentResponse decide(Long id, DecisionRequest r, Long actorId) {
        InvoiceAdjustment adj = adjustments.lockById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        if (adj.getStatus() != AdjustmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This request has already been " + adj.getStatus().name().toLowerCase());
        }
        if (adj.getRequestedBy().getId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own request");
        }
        User admin = users.findById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account not available"));
        Invoice inv = invoices.lockById(adj.getInvoice().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        String note = r.note() == null || r.note().isBlank() ? null : r.note().trim();

        if (!r.approve()) {
            if (note == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Give a reason for rejecting this request");
            }
            adj.setStatus(AdjustmentStatus.REJECTED);
        } else {
            check(inv, adj.getType(), adj.getAmount(), HttpStatus.CONFLICT);
            apply(inv, adj);
            adj.setStatus(AdjustmentStatus.APPROVED);
        }
        adj.setDecidedBy(admin);
        adj.setDecisionNote(note);
        adj.setDecidedAt(Instant.now());

        audit.log(actorId, r.approve() ? "ADJUSTMENT_APPROVED" : "ADJUSTMENT_REJECTED", "INVOICE", inv.getId(),
                adj.getType() + " " + adj.getAmount() + (note == null ? "" : ", note=" + note));
            
        notifications.toUser(adj.getRequestedBy().getId(), "ADJUSTMENT_DECIDED",
                "Your " + adj.getType().name().toLowerCase() + " request for invoice " + inv.getInvoiceNo()
                        + " was " + adj.getStatus().name().toLowerCase(), "/billing/invoices/" + inv.getId());
        return AdjustmentResponse.from(adj);
    }

    // ---------- rules ----------

    private void check(Invoice inv, AdjustmentType type, BigDecimal amount, HttpStatus status) {
        if (inv.getStatus() == InvoiceStatus.VOIDED) {
            throw new ApiException(status, "This invoice is voided");
        }
        switch (type) {
            case DISCOUNT -> {
                if (amount.compareTo(inv.balance()) > 0) {
                    throw new ApiException(status,
                            "A discount cannot be more than the outstanding balance of " + inv.balance());
                }
                if (amount.compareTo(inv.getTotal()) >= 0) {
                    throw new ApiException(status,
                            "A discount cannot reduce the invoice to zero. Void the invoice instead.");
                }
            }
            case VOID -> {
                if (inv.getAmountPaid().signum() > 0) {
                    throw new ApiException(status, "This invoice has payments. Refund them first, then void it.");
                }
            }
            case REFUND -> {
                if (inv.getAmountPaid().signum() <= 0) {
                    throw new ApiException(status, "There is nothing to refund on this invoice");
                }
                if (amount.compareTo(inv.getAmountPaid()) > 0) {
                    throw new ApiException(status,
                            "A refund cannot be more than the amount paid (" + inv.getAmountPaid() + ")");
                }
            }
        }
    }

    private void apply(Invoice inv, InvoiceAdjustment adj) {
        switch (adj.getType()) {
            case DISCOUNT -> {
                inv.setDiscount(inv.getDiscount().add(adj.getAmount()));
                inv.recalculate();
            }
            case VOID -> {
                inv.setStatus(InvoiceStatus.VOIDED);
                inv.setVoidReason(adj.getReason());
            }
            case REFUND -> {
                inv.setAmountPaid(inv.getAmountPaid().subtract(adj.getAmount()));
                inv.recalculate();
                String reference = "Refund: " + adj.getReason();
                Payment p = new Payment();
                p.setReceiptNo(String.format("RF-%06d", payments.nextRefundNumber()));
                p.setInvoiceId(inv.getId());
                p.setAmount(adj.getAmount().negate());
                p.setMethod(adj.getRefundMethod());
                p.setReference(reference.length() > 100 ? reference.substring(0, 100) : reference);
                p.setBalanceAfter(inv.balance());
                p.setCashier(adj.getRequestedBy());
                payments.save(p);
            }
        }
    }
}
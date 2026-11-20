package backend.billing;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class AdjustmentDtos {
    private AdjustmentDtos() {}

    public record AdjustmentRequest(
            @NotNull AdjustmentType type,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            PaymentMethod refundMethod,
            @NotBlank @Size(max = 255) String reason) {}

    public record DecisionRequest(
            @NotNull Boolean approve,
            @Size(max = 255) String note) {}

    public record AdjustmentResponse(Long id, Long invoiceId, String invoiceNo, String patientName,
                                     AdjustmentType type, BigDecimal amount, PaymentMethod refundMethod,
                                     String reason, AdjustmentStatus status, String requestedByName,
                                     String decidedByName, String decisionNote, Instant createdAt,
                                     Instant decidedAt) {
        public static AdjustmentResponse from(InvoiceAdjustment a) {
            return new AdjustmentResponse(a.getId(), a.getInvoice().getId(), a.getInvoice().getInvoiceNo(),
                    a.getInvoice().getPatient().getFullName(), a.getType(), a.getAmount(), a.getRefundMethod(),
                    a.getReason(), a.getStatus(), a.getRequestedBy().getFullName(),
                    a.getDecidedBy() == null ? null : a.getDecidedBy().getFullName(),
                    a.getDecisionNote(), a.getCreatedAt(), a.getDecidedAt());
        }
    }
}
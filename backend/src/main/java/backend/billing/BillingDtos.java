package backend.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillingDtos {
    private BillingDtos() {}

    public record ServiceChargeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull ChargeCategory category,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Boolean active) {}

    public record ServiceChargeResponse(Long id, String name, ChargeCategory category, BigDecimal price,
                                        boolean active) {
        public static ServiceChargeResponse from(ServiceCharge c) {
            return new ServiceChargeResponse(c.getId(), c.getName(), c.getCategory(), c.getPrice(), c.isActive());
        }
    }

    public record ServiceLine(@NotNull Long serviceChargeId, @NotNull @Min(1) @Max(100) Integer quantity) {}

    public record InvoiceRequest(
            Long appointmentId,
            @Valid @Size(max = 20) List<ServiceLine> services,
            Boolean includeDispensedMedicines,
            Boolean includeLabTests,
            Long admissionId) {}

    public record PaymentRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 100) String reference) {}

    public record ItemResponse(Long id, String itemType, String description, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {
        public static ItemResponse from(InvoiceItem i) {
            return new ItemResponse(i.getId(), i.getItemType(), i.getDescription(), i.getQuantity(),
                    i.getUnitPrice(), i.getLineTotal());
        }
    }

    public record PaymentResponse(Long id, String receiptNo, BigDecimal amount, PaymentMethod method,
                                  String reference, BigDecimal balanceAfter, String cashierName,
                                  Instant paidAt, boolean refund) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getReceiptNo(), p.getAmount(), p.getMethod(),
                    p.getReference(), p.getBalanceAfter(), p.getCashier().getFullName(), p.getPaidAt(),
                    p.getAmount().signum() < 0);
        }
    }

    public record InvoiceSummary(Long id, String invoiceNo, Long patientId, String patientNo, String patientName,
                                 BigDecimal total, BigDecimal amountPaid, BigDecimal balance,
                                 InvoiceStatus status, Instant createdAt) {
        public static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getInvoiceNo(), i.getPatient().getId(),
                    i.getPatient().getPatientNo(), i.getPatient().getFullName(), i.getTotal(),
                    i.getAmountPaid(), i.balance(), i.getStatus(), i.getCreatedAt());
        }
    }

    public record InvoiceDetail(Long id, String invoiceNo, Long appointmentId, Long patientId, String patientNo,
                                String patientName, BigDecimal subtotal, BigDecimal discount, BigDecimal total,
                                BigDecimal amountPaid, BigDecimal balance, InvoiceStatus status, String voidReason,
                                Instant createdAt, List<ItemResponse> items, List<PaymentResponse> payments) {}

    public record ReceiptResponse(String receiptNo, String invoiceNo, String patientNo, String patientName,
                                  BigDecimal amount, PaymentMethod method, String reference,
                                  BigDecimal balanceAfter, String cashierName, Instant paidAt, boolean refund) {}
}
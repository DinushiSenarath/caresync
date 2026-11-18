package backend.billing;

import backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter @Setter @NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    private Long appointmentId;

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    private String voidReason;
    private Long createdBy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public BigDecimal balance() {
        return total.subtract(amountPaid);
    }

    /** Recomputes total and status from the amounts. A voided invoice stays voided. */
    public void recalculate() {
        total = subtotal.subtract(discount);
        if (status == InvoiceStatus.VOIDED) return;
        if (amountPaid.signum() <= 0) {
            status = InvoiceStatus.UNPAID;
        } else if (amountPaid.compareTo(total) >= 0) {
            status = InvoiceStatus.PAID;
        } else {
            status = InvoiceStatus.PARTIALLY_PAID;
        }
    }
}
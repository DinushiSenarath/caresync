package backend.billing;

import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoice_adjustments")
@Getter @Setter @NoArgsConstructor
public class InvoiceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    private AdjustmentType type;

    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PaymentMethod refundMethod;

    private String reason;

    @Enumerated(EnumType.STRING)
    private AdjustmentStatus status = AdjustmentStatus.PENDING;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private String decisionNote;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant decidedAt;
}
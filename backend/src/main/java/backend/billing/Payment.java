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
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String receiptNo;
    private Long invoiceId;

    /** Positive for a payment, negative for a refund (used in step 7b). */
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private String reference;
    private BigDecimal balanceAfter;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cashier_id", nullable = false)
    private User cashier;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant paidAt;
}
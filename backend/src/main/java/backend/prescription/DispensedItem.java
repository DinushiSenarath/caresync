package backend.prescription;

import backend.pharmacy.MedicineBatch;
import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "dispensed_items")
@Getter @Setter @NoArgsConstructor
public class DispensedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long prescriptionItemId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private MedicineBatch batch;

    private int quantity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dispensed_by", nullable = false)
    private User dispensedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant dispensedAt;
}
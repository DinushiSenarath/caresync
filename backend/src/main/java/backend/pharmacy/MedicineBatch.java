package backend.pharmacy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "medicine_batches")
@Getter @Setter @NoArgsConstructor
public class MedicineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    private String batchNo;
    private LocalDate expiryDate;
    private int quantity;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant receivedAt;
}
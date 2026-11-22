package backend.lab;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "lab_tests")
@Getter @Setter @NoArgsConstructor
public class LabTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private LabCategory category;

    private String specimen;

    @Enumerated(EnumType.STRING)
    private ResultType resultType;

    private String unit;
    private BigDecimal refLow;
    private BigDecimal refHigh;
    private String refText;
    private BigDecimal price = BigDecimal.ZERO;
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
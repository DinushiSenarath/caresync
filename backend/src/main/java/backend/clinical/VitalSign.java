package backend.clinical;

import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vital_signs")
@Getter @Setter @NoArgsConstructor
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long appointmentId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    private BigDecimal temperature;
    private Integer pulse;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer respRate;
    private Integer spo2;
    private BigDecimal weight;
    private BigDecimal height;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant recordedAt;
}
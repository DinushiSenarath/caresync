package backend.ward;

import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bed_stays")
@Getter @Setter @NoArgsConstructor
public class BedStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "admission_id", nullable = false)
    private Admission admission;

    @ManyToOne(optional = false)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;

    private String wardName;
    private String bedNo;
    private BigDecimal dailyRate;
    private Instant fromAt;
    private Instant toAt;
    private String transferReason;

    @ManyToOne(optional = false)
    @JoinColumn(name = "moved_by", nullable = false)
    private User movedBy;
}
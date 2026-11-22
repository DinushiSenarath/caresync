package backend.lab;

import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "lab_request_items")
@Getter @Setter @NoArgsConstructor
public class LabRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private LabRequest request;

    private Long testId;
    private String testName;
    private String specimen;

    @Enumerated(EnumType.STRING)
    private ResultType resultType;

    private String unit;
    private BigDecimal refLow;
    private BigDecimal refHigh;
    private String refText;
    private BigDecimal price;

    private String resultValue;
    private BigDecimal resultNumber;

    @Enumerated(EnumType.STRING)
    private ResultFlag flag;

    private String resultComment;

    @ManyToOne
    @JoinColumn(name = "entered_by")
    private User enteredBy;

    private Instant enteredAt;
}
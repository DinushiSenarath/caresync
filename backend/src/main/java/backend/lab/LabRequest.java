package backend.lab;

import backend.patient.Patient;
import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lab_requests")
@Getter @Setter @NoArgsConstructor
public class LabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String requestNo;
    private Long appointmentId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    private LabPriority priority = LabPriority.ROUTINE;

    private String clinicalNotes;

    @Enumerated(EnumType.STRING)
    private LabStatus status = LabStatus.REQUESTED;

    private String sampleNo;

    @ManyToOne
    @JoinColumn(name = "collected_by")
    private User collectedBy;

    private Instant collectedAt;

    @ManyToOne
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    private Instant verifiedAt;
    private String cancelReason;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LabRequestItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
package backend.ward;

import backend.doctor.Doctor;
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
@Table(name = "admissions")
@Getter @Setter @NoArgsConstructor
public class Admission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String admissionNo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "responsible_doctor_id", nullable = false)
    private Doctor responsibleDoctor;

    private Long appointmentId;
    private String reason;

    @Enumerated(EnumType.STRING)
    private AdmissionStatus status = AdmissionStatus.ADMITTED;

    private Instant admittedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "admitted_by", nullable = false)
    private User admittedBy;

    @Column(columnDefinition = "text")
    private String dischargeSummary;

    @Column(columnDefinition = "text")
    private String dischargeInstructions;

    @ManyToOne
    @JoinColumn(name = "summary_by")
    private User summaryBy;

    private Instant summaryAt;
    private Instant dischargedAt;

    @ManyToOne
    @JoinColumn(name = "discharged_by")
    private User dischargedBy;

    private String cancelReason;

    @OneToMany(mappedBy = "admission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BedStay> stays = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
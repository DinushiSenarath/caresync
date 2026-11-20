package backend.registration;

import backend.patient.Gender;
import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "registration_requests")
@Getter @Setter @NoArgsConstructor
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String nic;
    private String phone;
    private String address;

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    private Long patientId;
    private String decisionNote;

    @ManyToOne
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    private Instant decidedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
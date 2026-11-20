package backend.portal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "activation_codes")
@Getter @Setter @NoArgsConstructor
public class ActivationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long patientId;
    private String codeHash;
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    private CodePurpose purpose = CodePurpose.ACTIVATION;

    /** Set when the code is used or replaced by a newer one. */
    private Instant usedAt;

    private int failedAttempts;
    private Long createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
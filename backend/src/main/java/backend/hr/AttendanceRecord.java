package backend.hr;

import backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_records")
@Getter @Setter @NoArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private LocalDate workDate;
    private Instant checkIn;
    private Instant checkOut;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    private AttendanceSource source;

    private String note;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
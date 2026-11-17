package backend.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findByAppointmentIdOrderByRecordedAtDesc(Long appointmentId);
    Optional<VitalSign> findFirstByAppointmentIdOrderByRecordedAtDesc(Long appointmentId);
}
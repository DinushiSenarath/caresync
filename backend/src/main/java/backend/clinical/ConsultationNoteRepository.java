package backend.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationNoteRepository extends JpaRepository<ConsultationNote, Long> {
    Optional<ConsultationNote> findByAppointmentId(Long appointmentId);
}
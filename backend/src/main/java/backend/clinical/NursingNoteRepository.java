package backend.clinical;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NursingNoteRepository extends JpaRepository<NursingNote, Long> {
    List<NursingNote> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);
}
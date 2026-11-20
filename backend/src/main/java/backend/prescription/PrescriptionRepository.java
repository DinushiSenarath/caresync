package backend.prescription;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByAppointmentIdOrderByIdAsc(Long appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prescription p where p.id = :id")
    Optional<Prescription> lockById(@Param("id") Long id);

    @Query("""
            select p from Prescription p
            where p.status in :statuses
            order by p.issuedAt asc, p.id asc
            """)
    Page<Prescription> queue(@Param("statuses") List<PrescriptionStatus> statuses, Pageable pageable);

    @Query("select p from Prescription p where p.appointment.patient.id = :patientId and p.status <> :excluded order by p.id desc")
    List<Prescription> findForPatient(@Param("patientId") Long patientId, @Param("excluded") PrescriptionStatus excluded);
}
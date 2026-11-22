package backend.ward;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    @Query(value = "SELECT nextval('admission_no_seq')", nativeQuery = true)
    Long nextNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admission a where a.id = :id")
    Optional<Admission> lockById(@Param("id") Long id);

    boolean existsByPatientIdAndStatus(Long patientId, AdmissionStatus status);

    @Query("""
            select a from Admission a
            where a.status in :statuses
              and (:anyDoctor = true or a.responsibleDoctor.id = :doctorId)
              and (lower(a.patient.fullName) like :pattern
                   or lower(a.patient.patientNo) like :pattern
                   or lower(a.admissionNo) like :pattern)
            """)
    Page<Admission> search(@Param("statuses") List<AdmissionStatus> statuses,
                           @Param("anyDoctor") boolean anyDoctor,
                           @Param("doctorId") Long doctorId,
                           @Param("pattern") String pattern,
                           Pageable pageable);

    long countByStatus(AdmissionStatus status);

    long countByAdmittedAtBetweenAndStatusNot(Instant from, Instant to, AdmissionStatus status);

    long countByStatusAndDischargedAtBetween(AdmissionStatus status, Instant from, Instant to);

    List<Admission> findByStatusAndDischargedAtAfterOrderByDischargedAtDesc(AdmissionStatus status, Instant after);
}
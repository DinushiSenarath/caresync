package backend.lab;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LabRequestRepository extends JpaRepository<LabRequest, Long> {

    @Query(value = "SELECT nextval('lab_request_no_seq')", nativeQuery = true)
    Long nextNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LabRequest r where r.id = :id")
    Optional<LabRequest> lockById(@Param("id") Long id);

    List<LabRequest> findByAppointmentIdOrderByIdAsc(Long appointmentId);

    List<LabRequest> findByPatientIdAndStatusOrderByVerifiedAtDesc(Long patientId, LabStatus status);

    @Query("""
            select r from LabRequest r
            where r.status in :statuses
              and (lower(r.patient.fullName) like :pattern
                   or lower(r.patient.patientNo) like :pattern
                   or lower(r.requestNo) like :pattern)
            """)
    Page<LabRequest> worklist(@Param("statuses") List<LabStatus> statuses,
                              @Param("pattern") String pattern, Pageable pageable);

    long countByStatus(LabStatus status);
    long countByStatusIn(Collection<LabStatus> statuses);
    long countByStatusAndPriority(LabStatus status, LabPriority priority);
    long countByStatusAndVerifiedAtBetween(LabStatus status, Instant from, Instant to);
    long countByAppointmentIdAndStatusIn(Long appointmentId, Collection<LabStatus> statuses);
}
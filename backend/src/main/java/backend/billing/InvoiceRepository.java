package backend.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Collection;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query(value = "SELECT nextval('invoice_no_seq')", nativeQuery = true)
    Long nextNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> lockById(@Param("id") Long id);

    boolean existsByAppointmentIdAndStatusNot(Long appointmentId, InvoiceStatus status);

    @Query("""
            select i from Invoice i
            where i.status in :statuses
              and (:anyPatient = true or i.patient.id = :patientId)
            order by i.id desc
            """)
    Page<Invoice> search(@Param("statuses") List<InvoiceStatus> statuses,
                         @Param("anyPatient") boolean anyPatient,
                         @Param("patientId") Long patientId,
                         Pageable pageable);

    long countByStatus(InvoiceStatus status);

    @Query("select sum(i.total - i.amountPaid) from Invoice i where i.status in :statuses")
    BigDecimal outstanding(@Param("statuses") Collection<InvoiceStatus> statuses);

    boolean existsByAdmissionIdAndStatusNot(Long admissionId, InvoiceStatus status);
}
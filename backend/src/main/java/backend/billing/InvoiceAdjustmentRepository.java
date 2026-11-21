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

public interface InvoiceAdjustmentRepository extends JpaRepository<InvoiceAdjustment, Long> {

    List<InvoiceAdjustment> findByInvoiceIdOrderByIdDesc(Long invoiceId);

    boolean existsByInvoiceIdAndStatus(Long invoiceId, AdjustmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from InvoiceAdjustment a where a.id = :id")
    Optional<InvoiceAdjustment> lockById(@Param("id") Long id);

    @Query("""
            select a from InvoiceAdjustment a
            where a.status in :statuses
            order by a.createdAt desc, a.id desc
            """)
    Page<InvoiceAdjustment> search(@Param("statuses") List<AdjustmentStatus> statuses, Pageable pageable);

    long countByStatus(AdjustmentStatus status);
}
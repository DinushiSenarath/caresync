package backend.pharmacy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    List<MedicineBatch> findByMedicineIdOrderByExpiryDateAscIdAsc(Long medicineId);

    boolean existsByMedicineIdAndBatchNoIgnoreCase(Long medicineId, String batchNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MedicineBatch b where b.id = :id")
    Optional<MedicineBatch> lockById(@Param("id") Long id);

    /** Used by dispensing in the next step: usable batches, earliest expiry first, rows locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from MedicineBatch b
            where b.medicine.id = :medicineId and b.quantity > 0 and b.expiryDate >= :today
            order by b.expiryDate asc, b.id asc
            """)
    List<MedicineBatch> lockUsableBatches(@Param("medicineId") Long medicineId, @Param("today") LocalDate today);

    @Query("""
            select b.medicine.id, sum(b.quantity) from MedicineBatch b
            where b.medicine.id in :ids and b.expiryDate >= :today
            group by b.medicine.id
            """)
    List<Object[]> stockByMedicine(@Param("ids") Collection<Long> ids, @Param("today") LocalDate today);

    @Query("""
            select b from MedicineBatch b
            where b.quantity > 0 and b.expiryDate <= :limit
            order by b.expiryDate asc, b.id asc
            """)
    List<MedicineBatch> findExpiringBy(@Param("limit") LocalDate limit);
}
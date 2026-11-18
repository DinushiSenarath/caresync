package backend.pharmacy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("""
            select s from StockMovement s
            where :anyMedicine = true or s.batch.medicine.id = :medicineId
            order by s.createdAt desc, s.id desc
            """)
    Page<StockMovement> search(@Param("anyMedicine") boolean anyMedicine,
                               @Param("medicineId") Long medicineId,
                               Pageable pageable);
}
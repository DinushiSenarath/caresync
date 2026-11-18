package backend.prescription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DispensedItemRepository extends JpaRepository<DispensedItem, Long> {
    List<DispensedItem> findByPrescriptionItemIdInOrderByDispensedAtAscIdAsc(Collection<Long> ids);
}
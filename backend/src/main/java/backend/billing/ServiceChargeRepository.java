package backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceChargeRepository extends JpaRepository<ServiceCharge, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<ServiceCharge> findAllByOrderByNameAsc();
    List<ServiceCharge> findByActiveTrueOrderByNameAsc();
}
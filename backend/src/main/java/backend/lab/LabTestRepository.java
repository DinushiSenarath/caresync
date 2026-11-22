package backend.lab;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<LabTest> findAllByOrderByNameAsc();
    List<LabTest> findByActiveTrueOrderByNameAsc();
}
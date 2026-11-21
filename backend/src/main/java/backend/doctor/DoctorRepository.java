package backend.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    boolean existsByUserId(Long userId);
    Optional<Doctor> findByUserId(Long userId);
    List<Doctor> findAllByOrderByIdAsc();
    List<Doctor> findByDepartmentIdOrderByIdAsc(Long departmentId);
    long countByActiveTrue();
}
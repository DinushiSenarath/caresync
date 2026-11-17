package backend.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    boolean existsByUserId(Long userId);
    List<Doctor> findAllByOrderByIdAsc();
    List<Doctor> findByDepartmentIdOrderByIdAsc(Long departmentId);
}
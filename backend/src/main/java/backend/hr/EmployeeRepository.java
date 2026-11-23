package backend.hr;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query(value = "SELECT nextval('employee_no_seq')", nativeQuery = true)
    Long nextNumber();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> lockById(@Param("id") Long id);

    Optional<Employee> findByUserId(Long userId);

    boolean existsByNic(String nic);

    boolean existsByNicAndIdNot(String nic, Long id);

    List<Employee> findByActiveTrueOrderByFullNameAsc();

    @Query("""
            select e from Employee e
            where (lower(e.fullName) like :pattern or lower(e.employeeNo) like :pattern
                   or lower(coalesce(e.nic, '')) like :pattern or e.phone like :pattern
                   or lower(e.jobTitle) like :pattern)
              and (:anyActive = true or e.active = :active)
              and (:anyDept = true or e.department.id = :deptId)
            order by e.fullName asc, e.id asc
            """)
    Page<Employee> search(@Param("pattern") String pattern,
                          @Param("anyActive") boolean anyActive, @Param("active") boolean active,
                          @Param("anyDept") boolean anyDept, @Param("deptId") Long deptId,
                          Pageable pageable);
}
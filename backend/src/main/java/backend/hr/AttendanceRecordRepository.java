package backend.hr;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescIdDesc(
            Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByWorkDateBetween(LocalDate from, LocalDate to);

    @Query("""
            select a from AttendanceRecord a
            where (:anyEmp = true or a.employee.id = :empId)
              and a.workDate between :from and :to
            order by a.workDate desc, a.id desc
            """)
    Page<AttendanceRecord> search(@Param("anyEmp") boolean anyEmp, @Param("empId") Long empId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to,
                                  Pageable pageable);
}
package backend.hr;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LeaveRequest l where l.id = :id")
    Optional<LeaveRequest> lockById(@Param("id") Long id);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :empId and l.status in :statuses
              and l.fromDate <= :to and l.toDate >= :from and l.id <> :excludeId
            """)
    boolean overlaps(@Param("empId") Long empId, @Param("statuses") Collection<LeaveStatus> statuses,
                     @Param("from") LocalDate from, @Param("to") LocalDate to,
                     @Param("excludeId") Long excludeId);

    @Query("""
            select coalesce(sum(l.days), 0) from LeaveRequest l
            where l.employee.id = :empId and l.leaveType = :type and l.status = :status
              and l.fromDate >= :yearStart and l.fromDate <= :yearEnd
            """)
    Long sumDays(@Param("empId") Long empId, @Param("type") LeaveType type, @Param("status") LeaveStatus status,
                 @Param("yearStart") LocalDate yearStart, @Param("yearEnd") LocalDate yearEnd);

    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :empId and l.status = :status
              and l.fromDate <= :date and l.toDate >= :date
            """)
    boolean onLeave(@Param("empId") Long empId, @Param("status") LeaveStatus status, @Param("date") LocalDate date);

    @Query("""
            select l from LeaveRequest l
            where l.status = :status and l.fromDate <= :to and l.toDate >= :from
            """)
    List<LeaveRequest> findOverlapping(@Param("status") LeaveStatus status,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select l from LeaveRequest l
            where l.status in :statuses and (:anyEmp = true or l.employee.id = :empId)
            order by l.createdAt desc, l.id desc
            """)
    Page<LeaveRequest> search(@Param("statuses") Collection<LeaveStatus> statuses,
                              @Param("anyEmp") boolean anyEmp, @Param("empId") Long empId, Pageable pageable);

    long countByStatus(LeaveStatus status);
}
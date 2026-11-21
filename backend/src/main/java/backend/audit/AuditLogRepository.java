package backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:anyAction = true or a.action = :action)
              and (:anyType = true or a.entityType = :entityType)
              and (:anyUser = true or a.userId = :userId)
              and (:anyEntity = true or a.entityId = :entityId)
              and a.createdAt >= :from and a.createdAt < :to
            order by a.createdAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("anyAction") boolean anyAction, @Param("action") String action,
                          @Param("anyType") boolean anyType, @Param("entityType") String entityType,
                          @Param("anyUser") boolean anyUser, @Param("userId") Long userId,
                          @Param("anyEntity") boolean anyEntity, @Param("entityId") Long entityId,
                          @Param("from") Instant from, @Param("to") Instant to,
                          Pageable pageable);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> distinctActions();
}
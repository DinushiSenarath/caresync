package backend.registration;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    boolean existsByStatusAndPhone(RegistrationStatus status, String phone);

    boolean existsByStatusAndNic(RegistrationStatus status, String nic);

    long countByStatus(RegistrationStatus status);

    Page<RegistrationRequest> findByStatusIn(Collection<RegistrationStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.id = :id")
    Optional<RegistrationRequest> lockById(@Param("id") Long id);
}
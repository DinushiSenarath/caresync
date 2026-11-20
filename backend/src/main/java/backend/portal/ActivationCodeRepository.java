package backend.portal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from ActivationCode c
            where c.patientId = :patientId and c.purpose = :purpose and c.usedAt is null
            order by c.id desc
            """)
    List<ActivationCode> lockOpenCodes(@Param("patientId") Long patientId, @Param("purpose") CodePurpose purpose);
}
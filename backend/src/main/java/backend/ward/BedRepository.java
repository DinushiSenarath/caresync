package backend.ward;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BedRepository extends JpaRepository<Bed, Long> {

    boolean existsByWardIdAndBedNoIgnoreCase(Long wardId, String bedNo);

    boolean existsByWardIdAndBedNoIgnoreCaseAndIdNot(Long wardId, String bedNo, Long id);

    @Query("select b from Bed b join fetch b.ward order by b.ward.id asc, b.bedNo asc")
    List<Bed> findAllWithWard();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bed b where b.id = :id")
    Optional<Bed> lockById(@Param("id") Long id);

    @Query("select count(b) from Bed b where b.active = true and b.ward.active = true")
    long countUsable();
}
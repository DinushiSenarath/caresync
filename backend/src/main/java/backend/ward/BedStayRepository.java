package backend.ward;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BedStayRepository extends JpaRepository<BedStay, Long> {

    boolean existsByBedIdAndToAtIsNull(Long bedId);

    Optional<BedStay> findByAdmissionIdAndToAtIsNull(Long admissionId);

    long countByToAtIsNull();

    @Query("select count(s) > 0 from BedStay s where s.bed.ward.id = :wardId and s.toAt is null")
    boolean existsOpenInWard(@Param("wardId") Long wardId);

    @Query("""
            select s from BedStay s
            join fetch s.bed b join fetch b.ward
            join fetch s.admission a join fetch a.patient
            where s.toAt is null
            """)
    List<BedStay> findOpenWithDetails();
}
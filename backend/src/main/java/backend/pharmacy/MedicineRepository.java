package backend.pharmacy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    @Query("""
            select m from Medicine m
            where (lower(m.name) like :pattern or lower(coalesce(m.category, '')) like :pattern)
              and (:includeInactive = true or m.active = true)
            order by m.name asc, m.id asc
            """)
    Page<Medicine> search(@Param("pattern") String pattern,
                          @Param("includeInactive") boolean includeInactive,
                          Pageable pageable);

    @Query("""
            select count(m) > 0 from Medicine m
            where lower(m.name) = lower(:name)
              and lower(coalesce(m.strength, '')) = lower(:strength)
              and m.form = :form and m.id <> :excludeId
            """)
    boolean identityExists(@Param("name") String name, @Param("strength") String strength,
                           @Param("form") MedicineForm form, @Param("excludeId") Long excludeId);

    List<Medicine> findByActiveTrueAndReorderLevelGreaterThan(int level);
}
package backend.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query(value = "SELECT nextval('patient_no_seq')", nativeQuery = true)
    Long nextSeq();

    @Query("""
            select p from Patient p
            where (lower(p.fullName) like :pattern
                   or lower(p.patientNo) like :pattern
                   or p.phone like :pattern
                   or lower(coalesce(p.nic, '')) like :pattern)
              and (:includeInactive = true or p.active = true)
            order by p.fullName asc, p.id asc
            """)
    Page<Patient> search(@Param("pattern") String pattern,
                         @Param("includeInactive") boolean includeInactive,
                         Pageable pageable);

    @Query("""
            select p from Patient p
            where (:nic <> '' and p.nic = :nic)
               or (lower(p.fullName) = lower(:name) and p.dateOfBirth = :dob)
            """)
    List<Patient> findPossibleDuplicates(@Param("nic") String nic,
                                         @Param("name") String name,
                                         @Param("dob") LocalDate dob);

    Optional<Patient> findByPatientNoIgnoreCase(String patientNo);
    Optional<Patient> findByUserId(Long userId);

    long countByActiveTrue();
}
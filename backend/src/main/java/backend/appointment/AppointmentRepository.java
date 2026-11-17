package backend.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @EntityGraph(attributePaths = {"patient", "doctor", "doctor.user", "doctor.department"})
    @Query("""
            select a from Appointment a
            where a.appointmentDate between :from and :to
              and a.status in :statuses
              and (:anyDoctor = true or a.doctor.id = :doctorId)
              and (:anyPatient = true or a.patient.id = :patientId)
            order by a.appointmentDate asc, a.startTime asc, a.id asc
            """)
    Page<Appointment> search(@Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("statuses") List<AppointmentStatus> statuses,
                             @Param("anyDoctor") boolean anyDoctor,
                             @Param("doctorId") Long doctorId,
                             @Param("anyPatient") boolean anyPatient,
                             @Param("patientId") Long patientId,
                             Pageable pageable);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId and a.appointmentDate = :date and a.startTime = :time
              and a.status <> :cancelled and a.id <> :excludeId
            """)
    boolean slotTaken(@Param("doctorId") Long doctorId,
                      @Param("date") LocalDate date,
                      @Param("time") LocalTime time,
                      @Param("cancelled") AppointmentStatus cancelled,
                      @Param("excludeId") Long excludeId);

    @Query("""
            select a.startTime from Appointment a
            where a.doctor.id = :doctorId and a.appointmentDate = :date and a.status <> :cancelled
            """)
    List<LocalTime> bookedTimes(@Param("doctorId") Long doctorId,
                                @Param("date") LocalDate date,
                                @Param("cancelled") AppointmentStatus cancelled);
}
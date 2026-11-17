package backend.doctor;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.department.Department;
import backend.department.DepartmentRepository;
import backend.doctor.DoctorDtos.*;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctors;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<DoctorResponse> list(Long departmentId, boolean activeOnly) {
        List<Doctor> list = departmentId == null
                ? doctors.findAllByOrderByIdAsc()
                : doctors.findByDepartmentIdOrderByIdAsc(departmentId);
        return list.stream()
                .filter(d -> !activeOnly || (d.isActive() && d.getUser().isActive()))
                .map(DoctorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(Long id) {
        return DoctorResponse.from(find(id));
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest r, Long actorId) {
        User u = users.findById(r.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() != Role.DOCTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This user does not have the DOCTOR role");
        }
        if (!u.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This user account is deactivated");
        }
        if (doctors.existsByUserId(u.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This user already has a doctor profile");
        }
        Department dep = departments.findById(r.departmentId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        if (!dep.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Department is not active");
        }
        checkTimes(r.startTime(), r.endTime());

        Doctor d = new Doctor();
        d.setUser(u);
        d.setDepartment(dep);
        d.setSpecialty(r.specialty());
        d.setWorkDays(r.workDays().toUpperCase());
        d.setStartTime(r.startTime());
        d.setEndTime(r.endTime());
        d.setSlotMinutes(r.slotMinutes());
        doctors.save(d);
        audit.log(actorId, "DOCTOR_CREATED", "DOCTOR", d.getId(), "userId=" + u.getId());
        return DoctorResponse.from(d);
    }

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest r, Long actorId) {
        Doctor d = find(id);
        checkTimes(r.startTime(), r.endTime());
        if (!d.getDepartment().getId().equals(r.departmentId())) {
            Department dep = departments.findById(r.departmentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
            if (!dep.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Department is not active");
            }
            d.setDepartment(dep);
        }
        d.setSpecialty(r.specialty());
        d.setWorkDays(r.workDays().toUpperCase());
        d.setStartTime(r.startTime());
        d.setEndTime(r.endTime());
        d.setSlotMinutes(r.slotMinutes());
        if (r.active() != null) d.setActive(r.active());
        audit.log(actorId, "DOCTOR_UPDATED", "DOCTOR", id, "active=" + d.isActive());
        return DoctorResponse.from(d);
    }

    private Doctor find(Long id) {
        return doctors.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor not found"));
    }

    private void checkTimes(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
    }
}
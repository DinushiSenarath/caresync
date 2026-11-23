package backend.hr;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.department.Department;
import backend.department.DepartmentRepository;
import backend.hr.HrDtos.*;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PagedResponse<EmployeeResponse> search(String q, String status, Long departmentId, int page, int size) {
        String s = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        boolean anyActive = "ALL".equals(s);
        boolean active = !"INACTIVE".equals(s);
        String cleaned = q == null ? "" : q.trim().replaceAll("[%_\\\\]", "");
        String pattern = "%" + cleaned.toLowerCase() + "%";
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Employee> result = employees.search(pattern, anyActive, active,
                departmentId == null, departmentId == null ? 0L : departmentId, pageable);
        Map<Long, User> userMap = userMap(result.getContent());
        return PagedResponse.of(result.map(e -> toResponse(e, userMap)));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        Employee e = find(id);
        return toResponse(e, userMap(List.of(e)));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest r, Long actorId) {
        Department d = department(r.departmentId());
        String nic = normNic(r.nic());
        if (nic != null && employees.existsByNic(nic)) {
            throw new ApiException(HttpStatus.CONFLICT, "An employee with this NIC already exists");
        }
        linkable(r.userId(), 0L);
        Employee e = new Employee();
        e.setEmployeeNo(String.format("EM-%04d", employees.nextNumber()));
        apply(e, r, d, nic);
        e.setActive(r.active() == null || r.active());
        employees.save(e);
        audit.log(actorId, "EMPLOYEE_CREATED", "EMPLOYEE", e.getId(), e.getEmployeeNo() + ", " + e.getJobTitle());
        return toResponse(e, userMap(List.of(e)));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest r, Long actorId) {
        Employee e = find(id);
        Department d = department(r.departmentId());
        String nic = normNic(r.nic());
        if (nic != null && employees.existsByNicAndIdNot(nic, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "An employee with this NIC already exists");
        }
        linkable(r.userId(), id);
        apply(e, r, d, nic);
        if (r.active() != null) e.setActive(r.active());
        audit.log(actorId, "EMPLOYEE_UPDATED", "EMPLOYEE", id, e.getEmployeeNo() + ", active=" + e.isActive());
        return toResponse(e, userMap(List.of(e)));
    }

    /** Staff logins that are not linked to an employee record yet. */
    @Transactional(readOnly = true)
    public List<UnlinkedUser> unlinkedUsers() {
        Set<Long> linked = employees.findAll().stream().map(Employee::getUserId)
                .filter(x -> x != null).collect(Collectors.toSet());
        return users.findAll().stream()
                .filter(u -> u.getRole() != Role.PATIENT && u.isActive() && !linked.contains(u.getId()))
                .map(u -> new UnlinkedUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name()))
                .toList();
    }

    /** The active employee record of the signed-in user. */
    @Transactional(readOnly = true)
    public Employee forUser(Long userId) {
        return employees.findByUserId(userId)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No active employee record is linked to your account. Please ask the administrator."));
    }

    // ---------- helpers ----------

    private Employee find(Long id) {
        return employees.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private Department department(Long id) {
        Department d = departments.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        if (!d.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This department is not active");
        }
        return d;
    }

    private void linkable(Long userId, Long selfId) {
        if (userId == null) return;
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Login account not found"));
        if (u.getRole() == Role.PATIENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A patient account cannot be linked to an employee");
        }
        employees.findByUserId(userId).ifPresent(other -> {
            if (!other.getId().equals(selfId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "That login account is already linked to " + other.getEmployeeNo());
            }
        });
    }

    private void apply(Employee e, EmployeeRequest r, Department d, String nic) {
        e.setFullName(r.fullName().trim());
        e.setNic(nic);
        e.setPhone(r.phone().trim());
        e.setEmail(clean(r.email()));
        e.setAddress(clean(r.address()));
        e.setJobTitle(r.jobTitle().trim());
        e.setDepartment(d);
        e.setEmploymentType(r.employmentType());
        e.setHireDate(r.hireDate());
        e.setUserId(r.userId());
        e.setEmergencyContactName(clean(r.emergencyContactName()));
        e.setEmergencyContactPhone(clean(r.emergencyContactPhone()));
    }

    private Map<Long, User> userMap(List<Employee> list) {
        Set<Long> ids = new HashSet<>();
        for (Employee e : list) if (e.getUserId() != null) ids.add(e.getUserId());
        Map<Long, User> map = new HashMap<>();
        if (!ids.isEmpty()) users.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private EmployeeResponse toResponse(Employee e, Map<Long, User> userMap) {
        User u = e.getUserId() == null ? null : userMap.get(e.getUserId());
        return new EmployeeResponse(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getNic(), e.getPhone(),
                e.getEmail(), e.getAddress(), e.getJobTitle(), e.getDepartment().getId(),
                e.getDepartment().getName(), e.getEmploymentType(), e.getHireDate(), e.getUserId(),
                u == null ? null : u.getUsername(), u == null ? null : u.getRole().name(),
                e.getEmergencyContactName(), e.getEmergencyContactPhone(), e.isActive());
    }

    private String normNic(String nic) {
        String c = clean(nic);
        return c == null ? null : c.toUpperCase();
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
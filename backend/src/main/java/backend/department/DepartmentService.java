package backend.department;

import backend.audit.AuditService;
import backend.common.ApiException;
import backend.department.DepartmentDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository repo;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list(boolean activeOnly) {
        List<Department> list = activeOnly ? repo.findByActiveTrueOrderByNameAsc() : repo.findAllByOrderByNameAsc();
        return list.stream().map(DepartmentResponse::from).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest r, Long actorId) {
        String name = r.name().trim();
        if (repo.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A department with this name already exists");
        }
        Department d = new Department();
        d.setName(name);
        d.setDescription(r.description());
        d.setActive(r.active() == null || r.active());
        repo.save(d);
        audit.log(actorId, "DEPARTMENT_CREATED", "DEPARTMENT", d.getId(), "name=" + name);
        return DepartmentResponse.from(d);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest r, Long actorId) {
        Department d = repo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Department not found"));
        String name = r.name().trim();
        if (repo.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A department with this name already exists");
        }
        d.setName(name);
        d.setDescription(r.description());
        if (r.active() != null) d.setActive(r.active());
        audit.log(actorId, "DEPARTMENT_UPDATED", "DEPARTMENT", id, "name=" + name);
        return DepartmentResponse.from(d);
    }
}
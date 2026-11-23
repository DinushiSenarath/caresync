package backend.hr;

import backend.common.PagedResponse;
import backend.hr.HrDtos.*;
import backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hr/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    public PagedResponse<EmployeeResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long departmentId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, departmentId, page, size);
    }

    @GetMapping("/unlinked-users")
    public List<UnlinkedUser> unlinked() {
        return service.unlinkedUsers();
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.create(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, JwtUtil.userId(jwt));
    }
}
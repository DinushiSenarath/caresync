package backend.lab;

import backend.lab.LabDtos.*;
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
@RequestMapping("/api/lab/tests")
@RequiredArgsConstructor
public class LabTestController {

    private final LabService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECH','ADMIN')")
    public List<LabTestResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listTests(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
    public LabTestResponse create(@Valid @RequestBody LabTestRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.createTest(request, JwtUtil.userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LAB_TECH','ADMIN')")
    public LabTestResponse update(@PathVariable Long id, @Valid @RequestBody LabTestRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return service.updateTest(id, request, JwtUtil.userId(jwt));
    }
}
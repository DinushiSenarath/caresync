package backend.dashboard;

import backend.dashboard.DashboardDtos.DashboardResponse;
import backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR','NURSE','PHARMACIST','CASHIER','LAB_TECH','WARD_STAFF')")
    public DashboardResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }
}
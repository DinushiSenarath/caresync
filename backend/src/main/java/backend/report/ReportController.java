package backend.report;

import backend.report.ReportDtos.ReportInfo;
import backend.report.ReportDtos.ReportResponse;
import backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','CASHIER','PHARMACIST','LAB_TECH')")
public class ReportController {

    private final ReportService service;

    @GetMapping
    public List<ReportInfo> list(@AuthenticationPrincipal Jwt jwt) {
        return service.available(JwtUtil.role(jwt));
    }

    @GetMapping("/{key}")
    public ReportResponse get(@PathVariable String key,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) Long doctorId,
                              @AuthenticationPrincipal Jwt jwt) {
        return service.generate(key, from, to, doctorId, JwtUtil.userId(jwt), JwtUtil.role(jwt));
    }
}
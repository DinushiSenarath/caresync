package backend.hr;

import backend.hr.HrDtos.AttendanceResponse;
import backend.hr.HrDtos.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class HrSelfService {

    private final EmployeeService employeeService;
    private final AttendanceRecordRepository records;
    private final LeaveRequestRepository leaveRepo;
    private final LeaveService leaveService;

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        Employee e = employeeService.forUser(userId);
        LocalDate today = HrDates.today();
        AttendanceResponse t = records.findByEmployeeIdAndWorkDate(e.getId(), today)
                .map(AttendanceResponse::from).orElse(null);
        boolean onLeave = leaveRepo.onLeave(e.getId(), LeaveStatus.APPROVED, today);
        return new MeResponse(e.getId(), e.getEmployeeNo(), e.getFullName(), e.getJobTitle(),
                e.getDepartment().getName(), e.getHireDate(), onLeave, t,
                leaveService.balances(e.getId(), today.getYear()));
    }
}
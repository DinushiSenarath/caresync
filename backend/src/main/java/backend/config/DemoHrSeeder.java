package backend.config;

import backend.department.Department;
import backend.department.DepartmentRepository;
import backend.hr.*;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoHrSeeder implements CommandLineRunner {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final AttendanceRecordRepository records;
    private final LeaveRequestRepository leaves;

    private final Map<String, Department> deptCache = new HashMap<>();
    private int phoneSeed = 710000001;

    @Override
    public void run(String... args) {
        if (employees.count() > 0) return;
        User admin = users.findByUsername("admin").orElse(null);
        if (admin == null) return;

        List<Employee> list = new ArrayList<>();
        list.add(emp("admin", null, "Hospital Administrator", "Administration", EmploymentType.PERMANENT, 6));
        list.add(emp("reception", null, "Front Desk Officer", "Reception", EmploymentType.PERMANENT, 3));
        list.add(emp("doctor", null, "Medical Officer", "General Medicine", EmploymentType.PERMANENT, 5));
        list.add(emp("nurse", null, "Staff Nurse", "Nursing", EmploymentType.PERMANENT, 4));
        list.add(emp("pharmacist", null, "Pharmacist", "Pharmacy", EmploymentType.PERMANENT, 2));
        list.add(emp("cashier", null, "Accountant", "Finance", EmploymentType.PERMANENT, 3));
        list.add(emp("lab", null, "Laboratory Technician", "Laboratory", EmploymentType.CONTRACT, 1));
        list.add(emp("ward", null, "Ward Clerk", "Nursing", EmploymentType.PERMANENT, 2));
        list.add(emp(null, "Kamal Rajapaksha", "Security Officer", "Administration", EmploymentType.CONTRACT, 2));
        list.add(emp(null, "Nimali Senanayake", "Housekeeping Supervisor", "Administration", EmploymentType.PERMANENT, 7));
        list.add(emp(null, "Sunil Gamage", "Ambulance Driver", "Administration", EmploymentType.PART_TIME, 1));

        LocalDate today = HrDates.today();
        int dayIndex = 0;
        for (int back = 1; back <= 10; back++) {
            LocalDate d = today.minusDays(back);
            if (!HrDates.isWorkingDay(d)) continue;
            dayIndex++;
            for (int i = 0; i < list.size(); i++) {
                Employee e = list.get(i);
                AttendanceRecord r = new AttendanceRecord();
                r.setEmployee(e);
                r.setWorkDate(d);
                r.setSource(AttendanceSource.MANUAL);
                r.setRecordedBy(admin);
                r.setNote("Demo data");
                if ((i + dayIndex) % 11 == 0) {
                    r.setStatus(AttendanceStatus.ABSENT);
                } else {
                    r.setStatus(AttendanceStatus.PRESENT);
                    r.setCheckIn(d.atTime(8, (i * 7 + dayIndex * 3) % 40).atZone(HrDates.ZONE).toInstant());
                    r.setCheckOut(d.atTime(16, 30 + (i % 20)).atZone(HrDates.ZONE).toInstant());
                }
                records.save(r);
            }
        }

        // one waiting leave request so the approval screen has something to show
        users.findByUsername("nurse").ifPresent(nurseUser -> {
            Employee nurse = list.get(3);
            LocalDate from = today.plusDays(10);
            LocalDate to = from.plusDays(2);
            if (from.getYear() == to.getYear()) {
                LeaveRequest l = new LeaveRequest();
                l.setEmployee(nurse);
                l.setLeaveType(LeaveType.ANNUAL);
                l.setFromDate(from);
                l.setToDate(to);
                l.setDays(Math.max(1, HrDates.workingDays(from, to)));
                l.setReason("Family event");
                l.setRequestedBy(nurseUser);
                leaves.save(l);
            }
        });
        log.info("Synthetic staff records are ready.");
    }

    private Employee emp(String username, String name, String job, String dept, EmploymentType type, int yearsAgo) {
        User u = username == null ? null : users.findByUsername(username).orElse(null);
        Employee e = new Employee();
        e.setEmployeeNo(String.format("EM-%04d", employees.nextNumber()));
        e.setFullName(u != null ? u.getFullName() : name);
        e.setPhone("0" + (phoneSeed++));
        e.setJobTitle(job);
        e.setDepartment(department(dept));
        e.setEmploymentType(type);
        e.setHireDate(LocalDate.now().minusYears(yearsAgo).minusDays(30));
        e.setUserId(u == null ? null : u.getId());
        return employees.save(e);
    }

    private Department department(String name) {
        return deptCache.computeIfAbsent(name, n -> departments.findAllByOrderByNameAsc().stream()
                .filter(d -> d.getName().equalsIgnoreCase(n))
                .findFirst()
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setName(n);
                    d.setDescription("Created for staff records");
                    return departments.save(d);
                }));
    }
}
package backend.config;

import backend.department.Department;
import backend.department.DepartmentRepository;
import backend.doctor.Doctor;
import backend.doctor.DoctorRepository;
import backend.patient.Gender;
import backend.patient.Patient;
import backend.patient.PatientRepository;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoClinicDataSeeder implements CommandLineRunner {

    private final DepartmentRepository departments;
    private final DoctorRepository doctors;
    private final UserRepository users;
    private final PatientRepository patients;

    @Override
    public void run(String... args) {
        Department general = dept("General Medicine", "Adult outpatient care");
        dept("Cardiology", "Heart care");
        dept("Paediatrics", "Children's health");

        users.findByUsername("doctor").ifPresent(u -> {
            if (!doctors.existsByUserId(u.getId())) {
                Doctor d = new Doctor();
                d.setUser(u);
                d.setDepartment(general);
                d.setSpecialty("General physician");
                doctors.save(d);
            }
        });

        if (patients.count() == 0) {
            patient("Kamal Jayasuriya", LocalDate.of(1972, 3, 14), Gender.MALE, "0771234567", "Penicillin");
            patient("Sanduni Herath", LocalDate.of(1995, 8, 2), Gender.FEMALE, "0712223344", null);
            patient("Ravi Kumar", LocalDate.of(2016, 5, 21), Gender.MALE, "0759988776", null);
            patient("Malini Dias", LocalDate.of(1955, 11, 30), Gender.FEMALE, "0701122334", "Aspirin");
            patient("Nuwan Perera", LocalDate.of(1988, 1, 9), Gender.MALE, "0765544332", null);
            log.info("Synthetic demo clinic data is ready.");
        }
    }

    private Department dept(String name, String description) {
        return departments.findAllByOrderByNameAsc().stream()
                .filter(d -> d.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setName(name);
                    d.setDescription(description);
                    return departments.save(d);
                });
    }

    private void patient(String name, LocalDate dob, Gender gender, String phone, String allergies) {
        Patient p = new Patient();
        p.setPatientNo(Patient.formatNumber(patients.nextSeq()));
        p.setFullName(name);
        p.setDateOfBirth(dob);
        p.setGender(gender);
        p.setPhone(phone);
        p.setAllergies(allergies);
        patients.save(p);
    }
}
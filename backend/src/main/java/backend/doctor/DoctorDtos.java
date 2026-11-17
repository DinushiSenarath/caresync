package backend.doctor;

import jakarta.validation.constraints.*;

import java.time.LocalTime;

public final class DoctorDtos {
    private DoctorDtos() {}

    private static final String DAYS = "^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN))*$";

    public record CreateDoctorRequest(
            @NotNull Long userId,
            @NotNull Long departmentId,
            @Size(max = 100) String specialty,
            @NotBlank @Pattern(regexp = DAYS, message = "use comma-separated days such as MON,TUE,WED") String workDays,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(5) @Max(120) Integer slotMinutes) {}

    public record UpdateDoctorRequest(
            @NotNull Long departmentId,
            @Size(max = 100) String specialty,
            @NotBlank @Pattern(regexp = DAYS, message = "use comma-separated days such as MON,TUE,WED") String workDays,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @Min(5) @Max(120) Integer slotMinutes,
            Boolean active) {}

    public record DoctorResponse(Long id, Long userId, String fullName, Long departmentId, String departmentName,
                                 String specialty, String workDays, LocalTime startTime, LocalTime endTime,
                                 int slotMinutes, boolean active) {
        public static DoctorResponse from(Doctor d) {
            return new DoctorResponse(d.getId(), d.getUser().getId(), d.getUser().getFullName(),
                    d.getDepartment().getId(), d.getDepartment().getName(), d.getSpecialty(),
                    d.getWorkDays(), d.getStartTime(), d.getEndTime(), d.getSlotMinutes(), d.isActive());
        }
    }
}
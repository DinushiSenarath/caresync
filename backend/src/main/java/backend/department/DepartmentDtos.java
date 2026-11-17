package backend.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DepartmentDtos {
    private DepartmentDtos() {}

    public record DepartmentRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            Boolean active) {}

    public record DepartmentResponse(Long id, String name, String description, boolean active) {
        public static DepartmentResponse from(Department d) {
            return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.isActive());
        }
    }
}
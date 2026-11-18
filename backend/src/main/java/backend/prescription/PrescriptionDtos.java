package backend.prescription;

import backend.pharmacy.MedicineForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PrescriptionDtos {
    private PrescriptionDtos() {}

    public record ItemRequest(
            @NotNull Long medicineId,
            @NotBlank @Size(max = 50) String dose,
            @Size(max = 30) String route,
            @NotBlank @Size(max = 50) String frequency,
            @Size(max = 50) String duration,
            @Size(max = 255) String instructions,
            @NotNull @Min(1) @Max(10000) Integer quantity) {}

    public record PrescriptionRequest(
            @Size(max = 500) String notes,
            @NotEmpty @Size(max = 20) @Valid List<ItemRequest> items) {}

    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {}

    public record DispenseLine(@NotNull Long itemId, @NotNull @Min(1) Integer quantity) {}

    public record DispenseRequest(@NotEmpty @Valid List<DispenseLine> lines) {}

    public record ItemResponse(Long id, Long medicineId, String medicineName, String strength, MedicineForm form,
                               String unit, String dose, String route, String frequency, String duration,
                               String instructions, int qtyPrescribed, int qtyDispensed, int qtyRemaining) {}

    public record PrescriptionResponse(Long id, Long appointmentId, Long patientId, String patientNo,
                                       String patientName, String doctorName, PrescriptionStatus status,
                                       String notes, Instant createdAt, Instant issuedAt,
                                       List<ItemResponse> items, List<String> allergyWarnings) {}

    public record QueueEntry(Long id, String patientNo, String patientName, String doctorName,
                             PrescriptionStatus status, Instant issuedAt, int itemCount) {}

    public record DispenseRecord(Long id, Long itemId, String medicineName, String batchNo, LocalDate expiryDate,
                                 int quantity, String dispensedBy, Instant dispensedAt) {}
}
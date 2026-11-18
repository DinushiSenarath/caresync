package backend.pharmacy;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PharmacyDtos {
    private PharmacyDtos() {}

    public record MedicineRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 50) String strength,
            @NotNull MedicineForm form,
            @NotBlank @Size(max = 20) String unit,
            @Size(max = 60) String category,
            @NotNull @Min(0) @Max(100000) Integer reorderLevel,
            Boolean active) {}

    public record MedicineResponse(Long id, String name, String strength, MedicineForm form, String unit,
                                   String category, int reorderLevel, boolean active,
                                   long availableStock, boolean lowStock) {}

    public record BatchRequest(
            @NotBlank @Size(max = 50) String batchNo,
            @NotNull LocalDate expiryDate,
            @NotNull @Min(1) @Max(1000000) Integer quantity) {}

    public record AdjustRequest(
            @NotNull @Min(-1000000) @Max(1000000) Integer quantityChange,
            @NotBlank @Size(max = 100) String reason) {}

    public record BatchResponse(Long id, Long medicineId, String medicineName, String batchNo,
                                LocalDate expiryDate, int quantity, boolean expired) {
        public static BatchResponse from(MedicineBatch b, LocalDate today) {
            return new BatchResponse(b.getId(), b.getMedicine().getId(), b.getMedicine().getName(),
                    b.getBatchNo(), b.getExpiryDate(), b.getQuantity(), b.getExpiryDate().isBefore(today));
        }
    }

    public record MovementResponse(Long id, Long batchId, String medicineName, String batchNo, MovementType type,
                                   int quantity, String reference, String userName, Instant createdAt) {
        public static MovementResponse from(StockMovement s) {
            return new MovementResponse(s.getId(), s.getBatch().getId(), s.getBatch().getMedicine().getName(),
                    s.getBatch().getBatchNo(), s.getType(), s.getQuantity(), s.getReference(),
                    s.getUser().getFullName(), s.getCreatedAt());
        }
    }

    public record AlertsResponse(List<MedicineResponse> lowStock, List<BatchResponse> nearExpiry,
                                 List<BatchResponse> expired) {}
}
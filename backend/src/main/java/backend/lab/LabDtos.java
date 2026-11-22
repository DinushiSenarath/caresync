package backend.lab;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LabDtos {
    private LabDtos() {}

    public record LabTestRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull LabCategory category,
            @NotBlank @Size(max = 40) String specimen,
            @NotNull ResultType resultType,
            @Size(max = 30) String unit,
            @Digits(integer = 9, fraction = 3) BigDecimal refLow,
            @Digits(integer = 9, fraction = 3) BigDecimal refHigh,
            @Size(max = 120) String refText,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            Boolean active) {}

    public record LabTestResponse(Long id, String name, LabCategory category, String specimen, ResultType resultType,
                                  String unit, BigDecimal refLow, BigDecimal refHigh, String refText,
                                  BigDecimal price, boolean active) {
        public static LabTestResponse from(LabTest t) {
            return new LabTestResponse(t.getId(), t.getName(), t.getCategory(), t.getSpecimen(), t.getResultType(),
                    t.getUnit(), t.getRefLow(), t.getRefHigh(), t.getRefText(), t.getPrice(), t.isActive());
        }
    }

    public record LabRequestCreate(
            @NotNull Long appointmentId,
            @NotEmpty @Size(max = 20) List<Long> testIds,
            LabPriority priority,
            @Size(max = 500) String clinicalNotes) {}

    public record CollectRequest(@Size(max = 40) String sampleNo) {}

    public record ResultEntry(
            @NotNull Long itemId,
            @Size(max = 500) String value,
            @Size(max = 500) String comment) {}

    public record ResultsRequest(@NotEmpty @Valid List<ResultEntry> results) {}

    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {}

    public record ItemResponse(Long id, Long testId, String testName, String specimen, ResultType resultType,
                               String unit, BigDecimal refLow, BigDecimal refHigh, String refText,
                               String resultValue, ResultFlag flag, String resultComment,
                               String enteredByName, Instant enteredAt) {}

    public record RequestResponse(Long id, String requestNo, Long appointmentId, Long patientId, String patientNo,
                                  String patientName, String requestedByName, LabPriority priority,
                                  String clinicalNotes, LabStatus status, String sampleNo, String collectedByName,
                                  Instant collectedAt, String verifiedByName, Instant verifiedAt,
                                  String cancelReason, Instant createdAt, List<ItemResponse> items) {}

    public record RequestSummary(Long id, String requestNo, String patientNo, String patientName,
                                 LabPriority priority, LabStatus status, String requestedByName,
                                 Instant createdAt, int itemCount) {}
}
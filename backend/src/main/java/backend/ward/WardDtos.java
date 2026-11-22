package backend.ward;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WardDtos {
    private WardDtos() {}

    public record WardRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull WardType wardType,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal dailyRate,
            Boolean active) {}

    public record BedRequest(@NotBlank @Size(max = 20) String bedNo, Boolean active) {}

    public record BedResponse(Long id, Long wardId, String bedNo, boolean active, boolean occupied,
                              Long admissionId, String admissionNo, Long patientId, String patientNo,
                              String patientName, Instant since) {}

    public record WardResponse(Long id, String name, WardType wardType, String description, BigDecimal dailyRate,
                               boolean active, int usableBeds, int occupied, int free, int outOfService,
                               List<BedResponse> beds) {}

    public record AdmissionRequest(
            @NotNull Long patientId,
            @NotNull Long responsibleDoctorId,
            @NotNull Long bedId,
            @NotBlank @Size(max = 500) String reason,
            Long appointmentId) {}

    public record TransferRequest(@NotNull Long bedId, @NotBlank @Size(max = 255) String reason) {}

    public record SummaryRequest(@NotBlank @Size(max = 8000) String summary, @Size(max = 4000) String instructions) {}

    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {}

    public record StayResponse(Long id, String wardName, String bedNo, BigDecimal dailyRate, Instant fromAt,
                               Instant toAt, String transferReason) {}

    public record AdmissionResponse(Long id, String admissionNo, Long patientId, String patientNo, String patientName,
                                    Long responsibleDoctorId, String responsibleDoctorName, String reason,
                                    AdmissionStatus status, Instant admittedAt, String admittedByName,
                                    String currentWard, String currentBed, Instant dischargedAt,
                                    String dischargedByName, boolean summaryWritten, String dischargeSummary,
                                    String dischargeInstructions, String cancelReason, List<StayResponse> stays) {}

    public record AdmissionSummary(Long id, String admissionNo, String patientNo, String patientName,
                                   String doctorName, String ward, String bed, Instant admittedAt,
                                   AdmissionStatus status, Instant dischargedAt, boolean summaryWritten) {}
}
package backend.dashboard;

import backend.appointment.AppointmentStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record QueueItem(Long appointmentId, LocalTime time, String patientNo, String patientName,
                            String doctorName, AppointmentStatus status, Boolean vitalsRecorded) {}

    public record DashboardResponse(String role, Instant generatedAt, Map<String, Object> counts,
                                    List<QueueItem> queue) {}
}
package backend.audit;

import java.time.Instant;

public final class AuditDtos {
    private AuditDtos() {}

    public record AuditLogResponse(Long id, Long userId, String userName, String action, String entityType,
                                   Long entityId, String details, Instant createdAt) {}
}
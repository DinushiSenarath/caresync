package backend.notification;

import java.time.Instant;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(Long id, String type, String message, String link, boolean read,
                                       Instant createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getLink(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record UnreadCountResponse(long unread) {}
}
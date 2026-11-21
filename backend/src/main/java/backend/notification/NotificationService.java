package backend.notification;

import backend.common.ApiException;
import backend.common.PagedResponse;
import backend.notification.NotificationDtos.NotificationResponse;
import backend.user.Role;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository users;

    // ---------- creating (joins the caller's transaction) ----------

    @Transactional
    public void toUser(Long userId, String type, String message, String link) {
        if (userId == null) return;
        repo.save(build(userId, type, message, link));
    }

    @Transactional
    public void toRole(Role role, String type, String message, String link) {
        for (User u : users.findByRoleAndActiveTrue(role)) {
            repo.save(build(u.getId(), type, message, link));
        }
    }

    private Notification build(Long userId, String type, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message.length() > 250 ? message.substring(0, 250) : message);
        n.setLink(link);
        return n;
    }

    // ---------- reading (always the signed-in user's own) ----------

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Notification> result = unreadOnly
                ? repo.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, pageable)
                : repo.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return PagedResponse.of(result.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long id, Long userId) {
        Notification n = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
        return NotificationResponse.from(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId, Instant.now());
    }
}
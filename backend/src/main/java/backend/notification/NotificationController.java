package backend.notification;

import backend.common.PagedResponse;
import backend.notification.NotificationDtos.NotificationResponse;
import backend.notification.NotificationDtos.UnreadCountResponse;
import backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PagedResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return service.list(JwtUtil.userId(jwt), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unread(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(service.unreadCount(JwtUtil.userId(jwt)));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse read(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.markRead(id, JwtUtil.userId(jwt));
    }

    @PostMapping("/read-all")
    public UnreadCountResponse readAll(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(JwtUtil.userId(jwt));
        return new UnreadCountResponse(0);
    }
}
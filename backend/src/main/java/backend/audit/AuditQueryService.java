package backend.audit;

import backend.audit.AuditDtos.AuditLogResponse;
import backend.common.PagedResponse;
import backend.user.User;
import backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Colombo");
    private static final Instant FAR_FUTURE = Instant.parse("2999-12-31T00:00:00Z");

    private final AuditLogRepository repo;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PagedResponse<AuditLogResponse> search(String action, String entityType, Long userId, Long entityId,
                                                  LocalDate from, LocalDate to, int page, int size) {
        String a = clean(action);
        String t = clean(entityType);
        Instant f = from == null ? Instant.EPOCH : from.atStartOfDay(ZONE).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));

        Page<AuditLog> result = repo.search(
                a == null, a == null ? "" : a,
                t == null, t == null ? "" : t,
                userId == null, userId == null ? 0L : userId,
                entityId == null, entityId == null ? 0L : entityId,
                f, end, pageable);

        Set<Long> ids = result.getContent().stream().map(AuditLog::getUserId)
                .filter(x -> x != null).collect(Collectors.toSet());
        Map<Long, String> names = users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return PagedResponse.of(result.map(l -> new AuditLogResponse(l.getId(), l.getUserId(),
                l.getUserId() == null ? null : names.get(l.getUserId()), l.getAction(), l.getEntityType(),
                l.getEntityId(), l.getDetails(), l.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return repo.distinctActions();
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
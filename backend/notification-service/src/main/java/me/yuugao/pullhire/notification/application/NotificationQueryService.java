package me.yuugao.pullhire.notification.application;

import me.yuugao.pullhire.notification.repo.NotificationLogRepository;
import me.yuugao.pullhire.notification.repo.PageResult;
import me.yuugao.pullhire.notification.domain.NotificationLog;
import me.yuugao.pullhire.notification.web.dto.NotificationLogItem;
import me.yuugao.pullhire.notification.web.dto.PageResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Read model for {@code GET /notifications} ({@code docs/api.md §3.8}).
 * Returns only the caller's own rows, paginated per {@code docs/api.md §1}.
 */
@Service
public class NotificationQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** Whitelist of sortable columns — never raw client input in SQL. */
    private static final Map<String, String> SORTABLE = Map.of(
            "created_at", "created_at",
            "sent_at", "sent_at",
            "id", "id",
            "status", "status",
            "template_code", "template_code",
            "event_type", "event_type");

    private final NotificationLogRepository logs;

    public NotificationQueryService(NotificationLogRepository logs) {
        this.logs = logs;
    }

    public PageResponse<NotificationLogItem> listOwn(long recipientUserId, int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        Sort parsed = Sort.parse(sort);
        PageResult<NotificationLog> result =
                logs.findByRecipientUserId(recipientUserId, safePage, safeSize, parsed.column(), parsed.ascending());

        List<NotificationLogItem> items = result.items().stream().map(NotificationLogItem::from).toList();
        return new PageResponse<>(items, safePage, safeSize, result.total());
    }

    /** Parsed {@code sort=field,dir}; unknown fields fall back to {@code created_at desc}. */
    private record Sort(String column, boolean ascending) {

        static Sort parse(String sort) {
            if (sort == null || sort.isBlank()) {
                return new Sort("created_at", false);
            }
            String[] parts = sort.split(",", 2);
            String column = SORTABLE.get(parts[0].trim());
            if (column == null) {
                return new Sort("created_at", false);
            }
            boolean ascending = parts.length == 2 && "asc".equalsIgnoreCase(parts[1].trim());
            return new Sort(column, ascending);
        }
    }
}

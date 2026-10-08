package me.yuugao.pullhire.notification.web;

import me.yuugao.pullhire.notification.application.NotificationQueryService;
import me.yuugao.pullhire.notification.security.JwtAuthFilter;
import me.yuugao.pullhire.notification.web.dto.NotificationLogItem;
import me.yuugao.pullhire.notification.web.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /notifications} ({@code docs/api.md §3.8}): the authenticated
 * caller's own outbound-email history, paginated ({@code docs/api.md §1}).
 */
@RestController
public class NotificationsController {

    private final NotificationQueryService query;

    public NotificationsController(NotificationQueryService query) {
        this.query = query;
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationLogItem> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at,desc") String sort,
            HttpServletRequest request) {
        return query.listOwn(requireUserId(request), page, size, sort);
    }

    /** User id set by {@link JwtAuthFilter}; 401 when the filter did not authenticate. */
    private static long requireUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
    }
}

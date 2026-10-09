package me.yuugao.pullhire.outreach.web;

import me.yuugao.pullhire.outreach.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated user id set by {@link JwtAuthFilter}. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** User id from the JWT filter; 401 when the request was not authenticated. */
    public static long id(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
    }
}

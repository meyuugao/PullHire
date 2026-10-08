package me.yuugao.pullhire.fsp.web;

import me.yuugao.pullhire.fsp.error.ApiException;
import me.yuugao.pullhire.fsp.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;

/** Resolves the authenticated candidate id set by {@link JwtAuthFilter}. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** User id from the JWT filter; 401 when the request was not authenticated. */
    public static long id(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw ApiException.unauthenticated("Требуется аутентификация");
    }
}

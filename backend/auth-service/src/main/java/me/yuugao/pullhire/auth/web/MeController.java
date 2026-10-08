package me.yuugao.pullhire.auth.web;

import me.yuugao.pullhire.auth.domain.User;
import me.yuugao.pullhire.auth.error.ApiException;
import me.yuugao.pullhire.auth.repo.UserRepository;
import me.yuugao.pullhire.auth.security.JwtAuthFilter;
import me.yuugao.pullhire.auth.web.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /auth/me} ({@code docs/api.md §3.1}). */
@RestController
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/auth/me")
    public MeResponse me(HttpServletRequest request) {
        long userId = requireUserId(request);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthenticated("Пользователь не найден"));
        return MeResponse.from(user);
    }

    /** User id set by {@link JwtAuthFilter}; 401 when the filter did not authenticate. */
    static long requireUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw ApiException.unauthenticated("Требуется аутентификация");
    }
}

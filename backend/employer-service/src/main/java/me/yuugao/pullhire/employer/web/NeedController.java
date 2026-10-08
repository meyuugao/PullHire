package me.yuugao.pullhire.employer.web;

import me.yuugao.pullhire.employer.application.NeedService;
import me.yuugao.pullhire.employer.web.dto.NeedRequest;
import me.yuugao.pullhire.employer.web.dto.NeedResponse;
import me.yuugao.pullhire.employer.web.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /employer/needs} ({@code docs/api.md §3.6}): owner-scoped need CRUD
 * ({@code docs/data_model.md §8.2}). Owner-scoped via the JWT {@code sub};
 * list is paginated ({@code docs/api.md §1}); POST returns 201 with the created
 * body (no {@code Location} header); DELETE is a plain row delete returning 204.
 * {@code @Valid} failures surface through {@link ApiExceptionHandler} as the
 * unified problem+json envelope (400 {@code validation_error}).
 */
@RestController
@RequestMapping("/employer/needs")
public class NeedController {

    private final NeedService needs;

    public NeedController(NeedService needs) {
        this.needs = needs;
    }

    @GetMapping
    public PageResponse<NeedResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at,desc") String sort,
            HttpServletRequest request) {
        NeedService.NeedPage result = needs.list(CurrentUser.id(request), page, size, sort);
        List<NeedResponse> items = result.items().stream().map(NeedResponse::from).toList();
        return new PageResponse<>(items, result.page(), result.size(), result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NeedResponse create(@Valid @RequestBody NeedRequest body, HttpServletRequest request) {
        return NeedResponse.from(needs.create(CurrentUser.id(request), body.toCommand()));
    }

    @GetMapping("/{id}")
    public NeedResponse get(@PathVariable long id, HttpServletRequest request) {
        return NeedResponse.from(needs.get(CurrentUser.id(request), id));
    }

    @PutMapping("/{id}")
    public NeedResponse update(@PathVariable long id, @Valid @RequestBody NeedRequest body,
                               HttpServletRequest request) {
        return NeedResponse.from(needs.update(CurrentUser.id(request), id, body.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, HttpServletRequest request) {
        needs.delete(CurrentUser.id(request), id);
    }
}

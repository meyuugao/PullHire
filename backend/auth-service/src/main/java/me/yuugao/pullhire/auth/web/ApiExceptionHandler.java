package me.yuugao.pullhire.auth.web;

import me.yuugao.pullhire.auth.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Ошибки отдаются единым конвертом ({@code docs/api.md §2}) с типом
 * {@code application/problem+json}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApi(ApiException ex, HttpServletRequest request) {
        return problem(ex.status(), ex.code(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<ErrorEnvelope.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorEnvelope.Detail(field.getField(), String.valueOf(field.getCode())))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation_error", "Некорректные данные запроса", details, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorEnvelope> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status.is4xxClientError() ? "client_error" : "server_error";
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return problem(status, code, message, null, request);
    }

    /**
     * Нарушение ограничения уникальности (например, гонка на UNIQUE {@code users.email}
     * при конкурентной регистрации) — это конфликт состояния, а не ошибка сервиса
     * ({@code docs/api.md §2}).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Нарушение целостности данных: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict",
                "Конфликт данных: нарушено ограничение уникальности", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Внутренняя ошибка сервиса", null, request);
    }

    private static ResponseEntity<ErrorEnvelope> problem(HttpStatus status, String code, String message,
                                                         List<ErrorEnvelope.Detail> details,
                                                         HttpServletRequest request) {
        String requestId = request.getHeader(ApiVersionFilter.REQUEST_ID_HEADER);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ErrorEnvelope.of(code, message, details, requestId == null ? "" : requestId));
    }
}

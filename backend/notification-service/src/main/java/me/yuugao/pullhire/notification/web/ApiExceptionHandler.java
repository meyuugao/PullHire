package me.yuugao.pullhire.notification.web;

import jakarta.servlet.http.HttpServletRequest;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneric(Exception ex, HttpServletRequest request) {
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

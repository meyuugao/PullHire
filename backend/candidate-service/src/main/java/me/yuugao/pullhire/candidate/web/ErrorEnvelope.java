package me.yuugao.pullhire.candidate.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Единый конверт ошибки PullHire ({@code docs/api.md §2}): тип
 * {@code application/problem+json}, единственный корневой ключ {@code error}.
 */
public record ErrorEnvelope(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<Detail> details,
            @JsonProperty("request_id") String requestId,
            String timestamp) {
    }

    public record Detail(String field, String issue) {
    }

    public static ErrorEnvelope of(String code, String message, List<Detail> details, String requestId) {
        String timestamp = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
        return new ErrorEnvelope(new ErrorBody(code, message, details, requestId, timestamp));
    }

    public static ErrorEnvelope of(String code, String message, String requestId) {
        return of(code, message, null, requestId);
    }
}

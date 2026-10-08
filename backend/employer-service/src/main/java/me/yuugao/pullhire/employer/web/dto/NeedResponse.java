package me.yuugao.pullhire.employer.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.employer.domain.Need;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** {@code /employer/needs} response ({@code docs/api.md §3.6}). {@code company_id} is never on the wire. */
public record NeedResponse(
        String id,
        String title,
        String description,
        @JsonProperty("specialization_code") String specializationCode,
        String grade,
        List<String> stack,
        String status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static NeedResponse from(Need need) {
        return new NeedResponse(
                Long.toString(need.id()),
                need.title(),
                need.description(),
                need.specializationCode(),
                need.grade(),
                toList(need.stack()),
                need.status(),
                need.createdAt(),
                need.updatedAt());
    }

    private static List<String> toList(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(Objects::nonNull).toList();
    }
}

package me.yuugao.pullhire.employer.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.yuugao.pullhire.employer.domain.NeedCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST|PUT /employer/needs} body ({@code docs/api.md §3.6}, {@code docs/data_model.md §8.2}).
 * {@code status} rides on this request as an optional field: {@code null} on POST
 * defaults to {@code active}; {@code null} on PUT keeps the current value
 * (service layer); a present value must be {@code active} or {@code archived}.
 */
public record NeedRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotBlank @JsonProperty("specialization_code") String specializationCode,
        @NotBlank String grade,
        List<String> stack,
        String status) {

    public NeedCommand toCommand() {
        return new NeedCommand(title, description, specializationCode, grade,
                stack == null ? null : stack.toArray(String[]::new), status);
    }
}

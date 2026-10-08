package me.yuugao.pullhire.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** {@code GET|PUT /auth/consents} response: current state of all four consent types. */
public record ConsentsResponse(
        @JsonProperty("user_id") String userId,
        List<ConsentItem> consents) {
}

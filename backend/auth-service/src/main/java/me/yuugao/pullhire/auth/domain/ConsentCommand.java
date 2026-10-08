package me.yuugao.pullhire.auth.domain;

/** A single consent mutation to append ({@code docs/data_model.md §3.4}). */
public record ConsentCommand(
        ConsentType type,
        boolean granted,
        String policyVersion) {
}

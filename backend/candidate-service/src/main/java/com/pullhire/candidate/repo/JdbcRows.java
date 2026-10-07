package com.pullhire.candidate.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Shared JDBC row helpers for the candidate repositories. */
final class JdbcRows {

    private JdbcRows() {
    }

    /** Reads a {@code timestamptz} column as UTC {@link Instant}, or {@code null}. */
    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * Converts to the {@link OffsetDateTime} type Spring JDBC/pgjdbc bind for
     * {@code timestamptz}; {@link Instant} itself has no SQL type mapping.
     */
    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Reads a {@code text[]} column as a {@code String[]}, or {@code null}. */
    static String[] strings(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        if (array == null) {
            return null;
        }
        Object value = array.getArray();
        return value instanceof String[] strings ? strings : null;
    }
}

package me.yuugao.pullhire.outreach.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Shared JDBC row helpers for the outreach repositories. */
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
}

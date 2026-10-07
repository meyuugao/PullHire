package com.pullhire.notification.repo;

import java.util.List;

/** Lightweight page of rows plus the unpaged total, for {@code docs/api.md §1}. */
public record PageResult<T>(List<T> items, long total) {
}

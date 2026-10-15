package com.ezaigc.hr.common.web.pagination;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class VisibleCursorPageCollector {

  private VisibleCursorPageCollector() {}

  public static <T> CursorPage<T> collect(
      StableCursor initialCursor,
      int pageSize,
      int fetchSize,
      CursorCodec codec,
      String scope,
      Function<StableCursor, List<T>> fetch,
      Function<List<T>, Set<String>> visibleIds,
      Function<T, Instant> sortTime,
      Function<T, String> id) {
    if (pageSize < 1 || fetchSize < 1) {
      throw new IllegalArgumentException("pageSize and fetchSize must be positive");
    }
    Objects.requireNonNull(fetch, "fetch");
    Objects.requireNonNull(codec, "codec");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(visibleIds, "visibleIds");
    Objects.requireNonNull(sortTime, "sortTime");
    Objects.requireNonNull(id, "id");

    List<T> authorized = new ArrayList<>(pageSize + 1);
    StableCursor scanCursor = initialCursor;
    while (authorized.size() <= pageSize) {
      List<T> rows = List.copyOf(fetch.apply(scanCursor));
      if (rows.isEmpty()) {
        break;
      }
      Set<String> allowedIds = Set.copyOf(visibleIds.apply(rows));
      for (T row : rows) {
        if (allowedIds.contains(id.apply(row))) {
          authorized.add(row);
          if (authorized.size() > pageSize) {
            break;
          }
        }
      }
      if (authorized.size() > pageSize || rows.size() < fetchSize) {
        break;
      }
      T lastScanned = rows.getLast();
      StableCursor nextScan = cursor(lastScanned, sortTime, id);
      if (sameCursor(scanCursor, nextScan)) {
        throw new IllegalStateException("keyset loader did not advance its cursor");
      }
      scanCursor = nextScan;
    }

    boolean hasMore = authorized.size() > pageSize;
    List<T> selected = List.copyOf(authorized.subList(0, Math.min(pageSize, authorized.size())));
    String nextCursor =
        hasMore ? cursor(selected.getLast(), sortTime, id).encode(codec, scope) : null;
    return new CursorPage<>(selected, nextCursor, hasMore);
  }

  private static <T> StableCursor cursor(
      T value, Function<T, Instant> sortTime, Function<T, String> id) {
    return new StableCursor(
        Objects.requireNonNull(sortTime.apply(value), "sortTime value"),
        Objects.requireNonNull(id.apply(value), "id value"));
  }

  private static boolean sameCursor(StableCursor left, StableCursor right) {
    return left != null
        && left.getSortTime().equals(right.getSortTime())
        && left.getId().equals(right.getId());
  }
}

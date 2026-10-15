package com.ezaigc.hr.common.web.pagination;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 基于“多查一条”结果构造稳定游标分页。
 */
public final class CursorPages {

    private CursorPages() {
    }

    /**
     * 截取当前页，并使用最后一条数据生成下一页游标。
     */
    public static <T> CursorPage<T> page(
            List<T> rows,
            int limit,
            CursorCodec codec,
            String scope,
            Function<? super T, Instant> sortTime,
            Function<? super T, String> id) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(sortTime, "sortTime");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(scope, "scope");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        boolean hasMore = rows.size() > limit;
        List<T> items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        String nextCursor =
                hasMore && !items.isEmpty()
                        ? new StableCursor(sortTime.apply(items.getLast()), id.apply(items.getLast()))
                        .encode(codec, scope)
                        : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    /**
     * 截取当前页后转换条目，并根据转换前的最后一条数据生成游标。
     */
    public static <S, T> CursorPage<T> page(
            List<S> rows,
            int limit,
            CursorCodec codec,
            String scope,
            Function<? super S, T> mapper,
            Function<? super S, Instant> sortTime,
            Function<? super S, String> id) {
        return page(rows, limit, codec, scope, sortTime, id).map(mapper);
    }
}

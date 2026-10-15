package com.ezaigc.hr.common.web.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.Getter;

@Getter
public final class CursorPage<T> {

    /**
     * 条目列表。
     */
    private final List<T> items;
    /**
     * 下一步游标。
     */
    private final String nextCursor;
    /**
     * 是否还有下一页数据。
     */
    private final boolean hasMore;

    public CursorPage(List<T> items, String nextCursor, boolean hasMore) {
        this.items = items == null ? List.of() : List.copyOf(items);
        this.nextCursor = hasMore ? nextCursor : null;
        this.hasMore = hasMore;
    }

    /**
     * 保留游标元数据并转换当前页条目。
     */
    public <R> CursorPage<R> map(Function<? super T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        List<R> mappedItems = items.stream().map(mapper).toList();
        return new CursorPage<>(mappedItems, nextCursor, hasMore);
    }
}

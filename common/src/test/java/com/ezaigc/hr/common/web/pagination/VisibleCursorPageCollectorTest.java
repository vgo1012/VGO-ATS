package com.ezaigc.hr.common.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class VisibleCursorPageCollectorTest {

  private static final CursorCodec CODEC =
      new SignedCursorCodec("visible-cursor-test-signing-key-32-bytes");
  private static final String SCOPE = "test.visible-cursor";

  @Test
  void continuesPastAnUnauthorizedBatchAndReturnsTheNextVisiblePage() {
    List<Row> rows = new ArrayList<>();
    for (int index = 30; index >= 1; index--) {
      rows.add(row(index, index <= 4));
    }

    var first =
        VisibleCursorPageCollector.collect(
            null,
            3,
            5,
            CODEC,
            SCOPE,
            cursor -> after(rows, cursor, 5),
            VisibleCursorPageCollectorTest::visibleIds,
            Row::sortTime,
            Row::id);

    assertThat(first.getItems()).extracting(Row::sequence).containsExactly(4, 3, 2);
    assertThat(first.isHasMore()).isTrue();

    var second =
        VisibleCursorPageCollector.collect(
            StableCursor.decode(CODEC, SCOPE, first.getNextCursor()),
            3,
            5,
            CODEC,
            SCOPE,
            cursor -> after(rows, cursor, 5),
            VisibleCursorPageCollectorTest::visibleIds,
            Row::sortTime,
            Row::id);

    assertThat(second.getItems()).extracting(Row::sequence).containsExactly(1);
    assertThat(second.isHasMore()).isFalse();
  }

  private static List<Row> after(List<Row> rows, StableCursor cursor, int limit) {
    return rows.stream()
        .filter(
            row ->
                cursor == null
                    || row.sortTime().isBefore(cursor.getSortTime())
                    || (row.sortTime().equals(cursor.getSortTime())
                        && row.id().compareTo(cursor.getId()) < 0))
        .limit(limit)
        .toList();
  }

  private static Set<String> visibleIds(List<Row> rows) {
    return Set.copyOf(rows.stream().filter(Row::visible).map(Row::id).toList());
  }

  private static Row row(int sequence, boolean visible) {
    return new Row(
        sequence,
        visible,
        Instant.parse("2026-09-02T00:00:00Z").plusSeconds(sequence),
        "%026d".formatted(sequence));
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Row {
    private final int sequence;
    private final boolean visible;
    private final Instant sortTime;
    private final String id;

    private int sequence() {
      return sequence;
    }

    private boolean visible() {
      return visible;
    }

    private Instant sortTime() {
      return sortTime;
    }

    private String id() {
      return id;
    }
  }
}

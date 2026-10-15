package com.ezaigc.hr.common.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPagesTest {

  private static final CursorCodec CODEC =
      new SignedCursorCodec("cursor-pages-test-signing-key-32-bytes");
  private static final String SCOPE = "test.cursor-pages";
  private static final Instant FIRST_TIME = Instant.parse("2026-09-03T01:00:00Z");
  private static final Instant SECOND_TIME = Instant.parse("2026-09-03T00:00:00Z");

  @Test
  void buildsCursorFromLastVisibleRowAndMapsItems() {
    Row first = new Row("01H00000000000000000000001", FIRST_TIME, "first");
    Row second = new Row("01H00000000000000000000002", SECOND_TIME, "second");

    CursorPage<String> page =
        CursorPages.page(
            List.of(first, second), 1, CODEC, SCOPE, Row::value, Row::time, Row::id);

    assertThat(page.getItems()).containsExactly("first");
    assertThat(page.isHasMore()).isTrue();
    StableCursor cursor = StableCursor.decode(CODEC, SCOPE, page.getNextCursor());
    assertThat(cursor.getSortTime()).isEqualTo(FIRST_TIME);
    assertThat(cursor.getId()).isEqualTo(first.id());
  }

  @Test
  void omitsCursorWhenThereIsNoNextPage() {
    Row row = new Row("01H00000000000000000000001", FIRST_TIME, "first");

    CursorPage<Row> page = CursorPages.page(List.of(row), 1, CODEC, SCOPE, Row::time, Row::id);

    assertThat(page.getItems()).containsExactly(row);
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
  }

  @Test
  void rejectsNonPositiveLimit() {
    assertThatThrownBy(
            () -> CursorPages.page(List.<Row>of(), 0, CODEC, SCOPE, Row::time, Row::id))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private record Row(String id, Instant time, String value) {}
}

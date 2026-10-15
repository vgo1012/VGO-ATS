package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.domain.dto.ResumeProjectionCursorDTO;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeFactProjectionSourceAdapterTest {

  @Test
  void loadsOneProjectionPageAsOneFactBatch() {
    ResumeFactQueryService facts = mock(ResumeFactQueryService.class);
    ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
    ResumeFactProjectionSourceAdapter adapter = new ResumeFactProjectionSourceAdapter();
    ReflectionTestUtils.setField(adapter, "facts", facts);
    ReflectionTestUtils.setField(adapter, "persistence", persistence);

    Instant firstCreatedAt = Instant.parse("2026-09-04T00:00:00Z");
    Instant secondCreatedAt = firstCreatedAt.plusSeconds(1);
    ResumeFactSnapshotData first = mock(ResumeFactSnapshotData.class);
    ResumeFactSnapshotData second = mock(ResumeFactSnapshotData.class);
    when(persistence.findCurrentFactSnapshotsAfter(Instant.EPOCH, "cursor", 3))
        .thenReturn(
            List.of(
                new ResumeProjectionCursorDTO("snapshot-1", firstCreatedAt, "UPLOAD", 2, 7,
                    "当前姓名", "current@example.com", "13900139000"),
                new ResumeProjectionCursorDTO("snapshot-2", secondCreatedAt, "IMPORT", 3)));
    Map<String, ResumeFactSnapshotData> bySnapshotId =
        Map.of("snapshot-1", first, "snapshot-2", second);
    when(facts.snapshotFactBatch(List.of("snapshot-1", "snapshot-2")))
        .thenReturn(bySnapshotId::get);

    var page = adapter.currentFactsAfter(Instant.EPOCH, "cursor", 2);

    assertThat(page.items()).extracting(item -> item.facts()).containsExactly(first, second);
    assertThat(page.hasMore()).isFalse();
    assertThat(page.items()).extracting(item -> item.resumeVersionNo()).containsExactly(2, 3);
    assertThat(page.items().getFirst().fullName()).isEqualTo("当前姓名");
    assertThat(page.items().getFirst().email()).isEqualTo("current@example.com");
    assertThat(page.items().getFirst().candidateVersion()).isEqualTo(7);
    verify(facts).snapshotFactBatch(List.of("snapshot-1", "snapshot-2"));
    verify(facts, never()).factsBySnapshotId(anyString());
  }
}

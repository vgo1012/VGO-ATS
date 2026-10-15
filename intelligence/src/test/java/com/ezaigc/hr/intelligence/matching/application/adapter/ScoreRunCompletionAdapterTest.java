package com.ezaigc.hr.intelligence.matching.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScoreRunCompletionAdapterTest {
  @Test
  void returnsOnlyFullySuccessfulRuns() {
    var persistence = mock(MatchRunPersistenceService.class);
    var adapter = new ScoreRunCompletionAdapter();
    ReflectionTestUtils.setField(adapter, "runs", persistence);
    var succeeded = run("succeeded", "SUCCEEDED");
    var partial = run("partial", "PARTIAL");
    var failed = run("failed", "FAILED");
    when(persistence.listByIds(List.of("succeeded", "partial", "failed")))
        .thenReturn(List.of(succeeded, partial, failed));

    assertThat(adapter.successfulRunIds(List.of("succeeded", "partial", "failed")))
        .containsExactly("succeeded");
  }

  @Test
  void acceptsOneThousandRunsAndReturnsOnlySucceededIds() {
    var persistence = mock(MatchRunPersistenceService.class);
    var adapter = new ScoreRunCompletionAdapter();
    ReflectionTestUtils.setField(adapter, "runs", persistence);
    var ids = IntStream.range(0, 1000)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();
    when(persistence.listByIds(ids)).thenReturn(IntStream.range(0, ids.size())
        .mapToObj(index -> run(ids.get(index), index == 999 ? "SUCCEEDED"
            : index % 2 == 0 ? "FAILED" : "PARTIAL")).toList());

    assertThat(adapter.successfulRunIds(ids)).containsExactly(ids.getLast());

    verify(persistence).listByIds(ids);
  }

  @Test
  void rejectsOneThousandAndOneRunsBeforeReadingPersistence() {
    var persistence = mock(MatchRunPersistenceService.class);
    var adapter = new ScoreRunCompletionAdapter();
    ReflectionTestUtils.setField(adapter, "runs", persistence);
    var ids = IntStream.range(0, 1001)
        .mapToObj(index -> String.format(Locale.ROOT, "%026d", index)).toList();

    assertThatThrownBy(() -> adapter.successfulRunIds(ids))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("评分运行最多1000项");

    verifyNoInteractions(persistence);
  }

  @Test
  void skipsPersistenceForEmptyOrNullInput() {
    var persistence = mock(MatchRunPersistenceService.class);
    var adapter = new ScoreRunCompletionAdapter();
    ReflectionTestUtils.setField(adapter, "runs", persistence);

    assertThat(adapter.successfulRunIds(List.of())).isEmpty();
    assertThat(adapter.successfulRunIds(null)).isEmpty();

    verifyNoInteractions(persistence);
  }

  private static MatchRun run(String id, String status) {
    var run = new MatchRun();
    run.setId(id);
    run.setStatus(status);
    return run;
  }
}

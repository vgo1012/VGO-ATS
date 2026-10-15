package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource.State;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource.StatePage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SearchStateReplayApplicationManagerTest {
  private final ResumeSearchStateSource source = mock(ResumeSearchStateSource.class);
  private final SearchIndexPort index = mock(SearchIndexPort.class);
  private final SearchIndexAuthorityApplicationManager authority = mock(SearchIndexAuthorityApplicationManager.class);
  private final StateReplayCheckpointService checkpoints = mock(StateReplayCheckpointService.class);
  private final StateReplayCheckpoint checkpoint = new StateReplayCheckpoint();

  SearchStateReplayApplicationManagerTest() {
    checkpoint.setIndexName("current-index");
    checkpoint.setLastCandidateId("completed-candidate");
    checkpoint.setVersion(3);
    when(checkpoints.checkpoint()).thenReturn(checkpoint);
    when(authority.currentTarget()).thenReturn(new SearchIndexTarget("current-index", 2));
    when(index.durable()).thenReturn(true);
    when(checkpoints.bindIndex(anyString(), anyLong(), anyString(), any())).thenReturn(true);
    when(checkpoints.advance(anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any())).thenReturn(true);
  }

  @Test
  void newManagerResumesPersistedPageAndWritesBeforeAdvancing() {
    when(source.currentStatesAfter("completed-candidate", 200)).thenReturn(page("next-candidate", true));
    assertThat(manager().replayNext(200)).isOne();
    var order = inOrder(checkpoints, index, source);
    order.verify(checkpoints).initialize(any());
    order.verify(index).ensureApprovedIndex(new SearchIndexTarget("current-index", 2));
    order.verify(index).durable();
    order.verify(checkpoints).checkpoint();
    order.verify(source).currentStatesAfter("completed-candidate", 200);
    order.verify(index).upsertBatch(eq(new SearchIndexTarget("current-index", 2)), any());
    order.verify(checkpoints).advance(eq("next-candidate"), eq(false), eq(1), eq(3L), anyString(), any());
    verify(checkpoints, never()).bindIndex(anyString(), anyLong(), anyString(), any());
  }

  @Test
  void missingCheckpointAfterInitializationIsRecreated() {
    checkpoint.setCompleted(true);
    when(checkpoints.checkpoint()).thenReturn(null, checkpoint);
    var manager = manager();
    ReflectionTestUtils.setField(manager, "initialized", true);

    assertThat(manager.replayNext(200)).isZero();

    verify(checkpoints).initialize(any(StateReplayCheckpoint.class));
    verifyNoInteractions(source);
  }

  @Test
  void changedIndexStartsFromBeginningAndCompletesEvenAnEmptyPage() {
    when(authority.currentTarget()).thenReturn(new SearchIndexTarget("new-index", 3));
    checkpoint.setCompleted(true);
    when(source.currentStatesAfter(MIN_ID, 500)).thenReturn(new StatePage(List.of(), false));
    assertThat(manager().replayNext(Integer.MAX_VALUE)).isZero();
    verify(checkpoints).bindIndex(eq("new-index"), eq(3L), anyString(), any());
    verify(checkpoints).advance(eq(MIN_ID), eq(true), eq(0), eq(4L), anyString(), any());
    verify(index, never()).upsertBatch(any(SearchIndexTarget.class), any());
  }

  @Test
  void remoteFailureDoesNotAdvanceAndRestartReplaysTheSamePage() {
    when(source.currentStatesAfter("completed-candidate", 200)).thenReturn(page("next-candidate", false));
    doThrow(new IllegalStateException("synthetic write failure")).when(index).upsertBatch(any(SearchIndexTarget.class), any());
    assertThatThrownBy(() -> manager().replayNext(200)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> manager().replayNext(200)).isInstanceOf(IllegalStateException.class);
    verify(checkpoints, never()).advance(anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any());
    assertThat(checkpoint.getLastCandidateId()).isEqualTo("completed-candidate");
  }

  @Test
  void losingIndexBindingCannotReadOrWriteAPage() {
    when(authority.currentTarget()).thenReturn(new SearchIndexTarget("new-index", 3));
    when(checkpoints.bindIndex(anyString(), anyLong(), anyString(), any())).thenReturn(false);
    assertThat(manager().replayNext(200)).isZero();
    verifyNoInteractions(source);
    verify(index, never()).upsertBatch(any(SearchIndexTarget.class), any());
  }

  @Test
  void losingAdvanceNeverClaimsPageCompletion() {
    when(source.currentStatesAfter("completed-candidate", 200)).thenReturn(page("next-candidate", false));
    when(checkpoints.advance(anyString(), anyBoolean(), anyInt(), anyLong(), anyString(), any())).thenReturn(false);
    assertThatThrownBy(() -> manager().replayNext(200)).isInstanceOf(IllegalStateException.class)
        .hasMessage("search state replay checkpoint changed");
  }

  @Test
  void durableCompletedIndexDoesNotScanAgainButEmptyMemoryIndexMustRebuild() {
    checkpoint.setCompleted(true);
    assertThat(manager().replayNext(200)).isZero();
    verifyNoInteractions(source);
    when(index.durable()).thenReturn(false);
    when(source.currentStatesAfter(MIN_ID, 200)).thenReturn(page("next-candidate", false));
    assertThat(manager().replayNext(200)).isOne();
    verify(checkpoints).bindIndex(eq("current-index"), eq(3L), anyString(), any());
  }

  private StatePage page(String candidateId, boolean hasMore) {
    return new StatePage(List.of(new State(candidateId, 1, null, null, null, null, 0, true, null, Instant.EPOCH, null)), hasMore);
  }

  private SearchStateReplayApplicationManager manager() {
    var manager = new SearchStateReplayApplicationManager();
    ReflectionTestUtils.setField(manager, "source", source);
    ReflectionTestUtils.setField(manager, "index", index);
    ReflectionTestUtils.setField(manager, "authority", authority);
    ReflectionTestUtils.setField(manager, "checkpoints", checkpoints);
    ReflectionTestUtils.setField(manager, "factory", new SearchDocumentFactory());
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    return manager;
  }
}

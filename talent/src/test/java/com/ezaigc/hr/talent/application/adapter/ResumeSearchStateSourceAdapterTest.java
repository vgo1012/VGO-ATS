package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.domain.dto.ResumeSearchStateRow;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeSearchStateSourceAdapterTest {
  private final ResumeParsingPersistenceService persistence = mock(ResumeParsingPersistenceService.class);
  private final ResumeFactQueryService facts = mock(ResumeFactQueryService.class);
  private final ResumeSearchStateSourceAdapter source = new ResumeSearchStateSourceAdapter();

  ResumeSearchStateSourceAdapterTest() {
    ReflectionTestUtils.setField(source, "persistence", persistence);
    ReflectionTestUtils.setField(source, "facts", facts);
  }

  @Test
  void replayLoadsOneBoundedBatchAndRetainsDeletedAndUnparsedRows() {
    var first = row("a", "ACTIVE", "chosen");
    var deleted = row("b", "DELETED", "old");
    var unparsed = row("c", "ACTIVE", null);
    when(persistence.findCurrentSearchStatesAfter("", 4)).thenReturn(List.of(first, deleted, unparsed, row("d", "ACTIVE", "next")));
    var body = mock(ResumeFactSnapshotData.class);
    when(facts.snapshotFactBatch(List.of("chosen"))).thenReturn(id -> body);
    var page = source.currentStatesAfter("", 3);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.items()).hasSize(3);
    assertThat(page.items().getFirst().facts()).isSameAs(body);
    assertThat(page.items().getFirst().fullName()).isEqualTo("姓名-a");
    assertThat(page.items().getFirst().email()).isEqualTo("a@example.com");
    assertThat(page.items().getFirst().candidateVersion()).isEqualTo(7);
    assertThat(page.items().get(1).deleted()).isTrue();
    assertThat(page.items().get(1).facts()).isNull();
    assertThat(page.items().getLast().facts()).isNull();
    verify(facts).snapshotFactBatch(List.of("chosen"));
    verify(facts, never()).factsBySnapshotId(anyString());
    verify(persistence, never()).findCurrentSearchState(anyString());
  }

  @Test
  void replayRejectsInvalidBoundsAndMissingSelectedFacts() {
    assertThatThrownBy(() -> source.currentStatesAfter(null, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> source.currentStatesAfter("a".repeat(27), 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> source.currentStatesAfter("", 501)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(persistence, facts);
    when(persistence.findCurrentSearchStatesAfter("", 2)).thenReturn(List.of(row("a", "ACTIVE", "missing")));
    when(facts.snapshotFactBatch(List.of("missing"))).thenReturn(id -> null);
    assertThatThrownBy(() -> source.currentStatesAfter("", 1)).isInstanceOf(IllegalStateException.class);
  }

  private ResumeSearchStateRow row(String id, String status, String factId) {
    var row = new ResumeSearchStateRow();
    row.setCandidateId(id);
    row.setCandidateStatus(status);
    row.setCandidateVersion(7);
    row.setFullName("姓名-" + id);
    row.setEmail(id + "@example.com");
    row.setPhone("1380013800" + id.length());
    row.setFactSnapshotId(factId);
    return row;
  }

  @Test
  void deletedAndUnparsedStatesNeverReadAnOldFactBody() {
    var row = new ResumeSearchStateRow();
    row.setCandidateId("candidate"); row.setCandidateStatus("DELETED");
    row.setFactSnapshotId("old-fact");
    when(persistence.findCurrentSearchState("candidate")).thenReturn(row);
    assertThat(source.currentState("candidate").deleted()).isTrue();
    assertThat(source.currentState("candidate").fullName()).isNull();
    assertThat(source.currentState("candidate").facts()).isNull();
    row.setCandidateStatus("ACTIVE"); row.setFactSnapshotId(null); row.setResumeVersionNo(2);
    var current = source.currentState("candidate");
    assertThat(current.deleted()).isFalse();
    assertThat(current.resumeVersionNo()).isEqualTo(2);
    assertThat(current.facts()).isNull();
    verifyNoInteractions(facts);
  }

  @Test
  void loadsTheSnapshotChosenByTheStateQueryAndDoesNotHideAMissingBody() {
    var row = new ResumeSearchStateRow();
    row.setCandidateId("candidate"); row.setCandidateStatus("ACTIVE"); row.setFactSnapshotId("chosen");
    when(persistence.findCurrentSearchState("candidate")).thenReturn(row);
    assertThatThrownBy(() -> source.currentState("candidate")).isInstanceOf(IllegalStateException.class);
    var body = mock(ResumeFactSnapshotData.class);
    when(facts.factsBySnapshotId("chosen")).thenReturn(body);
    assertThat(source.currentState("candidate").facts()).isSameAs(body);
    assertThat(source.currentState("unknown")).isNull();
  }
}

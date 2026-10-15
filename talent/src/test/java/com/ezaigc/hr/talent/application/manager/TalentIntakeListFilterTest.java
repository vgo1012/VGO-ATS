package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.IntakeBatchPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TalentIntakeListFilterTest {
  private static final String INTAKE_A = "01JINTAKEA0000000000000000";
  private static final String INTAKE_B = "01JINTAKEB0000000000000000";
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
  private final TalentIntakePersistenceService persistence =
      mock(TalentIntakePersistenceService.class);
  private final IntakeBatchPersistenceService batches = mock(IntakeBatchPersistenceService.class);

  @BeforeEach
  void setup() {
    var actor = new CurrentActorSnapshot(
        "hr", "HR", "hr@example.invalid", "dept", Set.of(), Set.of("report"), Set.of("dept"),
        Set.of("RECRUITER"), Set.of("talent.read", "talent.manage"), false, false);
    when(authorization.currentActor()).thenReturn(actor);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "intakeBatchPersistenceService", batches);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    ReflectionTestUtils.setField(manager, "cursorCodec", mock(CursorCodec.class));
  }

  @Test
  void countsAllMatchingIntakesWithTheSameFiltersAndAuthorizationScope() {
    var from = Instant.parse("2026-09-01T00:00:00Z");
    var to = Instant.parse("2026-09-02T00:00:00Z");
    when(persistence.countIntakes(null, "READY", "PENDING", true, "java", "张三", from, to,
        "hr", false, Set.of(), Set.of())).thenReturn(143L);

    var result = manager.countIntakes(
        "ready", "pending", true, " java ", " 张三 ", from, to);

    assertThat(result.getCount()).isEqualTo(143L);
    assertThat(result.isHasMore()).isFalse();
    assertThat(result.getNextCursor()).isNull();
    verify(authorization).requirePermission("talent.read");
    verify(persistence).countIntakes(null, "READY", "PENDING", true, "java", "张三", from, to,
        "hr", false, Set.of(), Set.of());
  }

  @Test
  void countRejectsInvalidFiltersLikeTheList() {
    assertThatThrownBy(() -> manager.countIntakes(
        null, "UNKNOWN", null, null, null, null, null))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_IMPORT_STATUS_INVALID"));
    var at = Instant.parse("2026-09-01T00:00:00Z");
    assertThatThrownBy(() -> manager.countIntakes(null, null, null, null, null, at, at))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_TIME_RANGE_INVALID"));
  }

  @Test
  void appliesIntakeFiltersBeforeTheBoundedQuery() {
    var from = Instant.parse("2026-09-01T00:00:00Z");
    var to = Instant.parse("2026-09-02T00:00:00Z");
    when(persistence.findIntakes(
        eq(null), eq("READY"), eq(null), eq(null), eq("java"), eq("张三"), eq(from), eq(to),
        eq("hr"), eq(false),
        eq(Set.of()), eq(Set.of()), eq(null), eq(null), eq(21)))
        .thenReturn(List.of());

    manager.intakes("ready", " java ", " 张三 ", from, to, null, 20);

    verify(persistence).findIntakes(
        null, "READY", null, null, "java", "张三", from, to, "hr", false, Set.of(),
        Set.of(), null, null, 21);
  }

  @Test
  void appliesImportStatusBeforePaginationAndRejectsUnknownValues() {
    when(persistence.findIntakes(null, null, "PENDING", true, null, null, null, null, "hr", false,
        Set.of(), Set.of(), null, null, 21)).thenReturn(List.of());

    manager.intakes(null, "pending", true, null, null, null, null, null, 20);

    verify(persistence).findIntakes(null, null, "PENDING", true, null, null, null, null, "hr", false,
        Set.of(), Set.of(), null, null, 21);
    assertThatThrownBy(() -> manager.intakes(
        null, "UNKNOWN", null, null, null, null, null, null, 20))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_IMPORT_STATUS_INVALID"));
  }

  @Test
  void scopesBatchIntakesBeforePaginationAndCount() {
    String batchId = "01JBATCH000000000000000000";
    when(persistence.findIntakes(batchId, null, null, null, null, null, null, null,
        "hr", false, Set.of(), Set.of(), null, null, 21)).thenReturn(List.of());

    manager.intakes(batchId, null, null, null, null, null, null, null, null, 20);
    manager.countIntakes(batchId, null, null, null, null, null, null, null);

    verify(persistence).findIntakes(batchId, null, null, null, null, null, null, null,
        "hr", false, Set.of(), Set.of(), null, null, 21);
    verify(persistence).countIntakes(batchId, null, null, null, null, null, null, null,
        "hr", false, Set.of(), Set.of());
  }

  @Test
  void batchCursorCannotBeReusedForAnotherTask() {
    String firstBatch = "01JBATCH000000000000000000";
    String otherBatch = "01JBATCH000000000000000001";
    var firstIntake = new ResumeIntake();
    firstIntake.setId(INTAKE_A);
    firstIntake.setCreateTime(Instant.parse("2026-09-01T00:00:00Z"));
    ReflectionTestUtils.setField(manager, "cursorCodec",
        new SignedCursorCodec("intake-batch-disposable-test-key"));
    when(persistence.findIntakes(firstBatch, null, null, null, null, null, null, null,
        "hr", false, Set.of(), Set.of(), null, null, 2))
        .thenReturn(List.of(firstIntake, firstIntake));

    var page = manager.intakes(firstBatch, null, null, null, null, null, null, null, null, 1);

    assertThat(page.getNextCursor()).isNotBlank();
    assertThatThrownBy(() -> manager.intakes(otherBatch, null, null, null, null, null,
        null, null, page.getNextCursor(), 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }

  @Test
  void batchTaskListUsesDefaultOwnerScopeAndStableCursor() {
    var first = new IntakeBatch();
    first.setId("01JBATCH000000000000000000");
    first.setCreateTime(Instant.parse("2026-09-01T00:00:00Z"));
    var second = new IntakeBatch();
    second.setId("01JBATCH000000000000000001");
    second.setCreateTime(Instant.parse("2026-08-31T00:00:00Z"));
    ReflectionTestUtils.setField(manager, "cursorCodec",
        new SignedCursorCodec("batch-list-disposable-test-signing-key"));
    when(batches.findBatches("hr", false, Set.of(), null, null, 2))
        .thenReturn(List.of(first, second));

    var page = manager.batchResponses(null, 1);

    assertThat(page.getItems()).extracting("id").containsExactly(first.getId());
    assertThat(page.getNextCursor()).isNotBlank();
    verify(authorization).requirePermission("talent.read");
    verify(batches).findBatches("hr", false, Set.of(), null, null, 2);
  }

  @Test
  void batchProgressIsVisibleOnlyThroughBatchReadAuthorization() {
    String batchId = "01JBATCH000000000000000000";
    var batch = new IntakeBatch();
    batch.setId(batchId);
    batch.setDefaultOwnerUserId("hr");
    batch.setOwnerDepartmentId("dept");
    batch.setParsingCount(2);
    batch.setProcessingCount(3);
    batch.setParsedCount(3);
    batch.setCompletedCount(1);
    batch.setIgnoredCount(1);
    batch.setAttentionCount(1);
    when(batches.findBatchWithProgress(batchId)).thenReturn(batch);

    var result = manager.batchResponse(batchId);

    assertThat(result.getParsingCount()).isEqualTo(2);
    assertThat(result.getProcessingCount()).isEqualTo(3);
    assertThat(result.getParsedCount()).isEqualTo(3);
    assertThat(result.getCompletedCount()).isEqualTo(1);
    assertThat(result.getIgnoredCount()).isEqualTo(1);
    assertThat(result.getAttentionCount()).isEqualTo(1);
    verify(authorization).requireAccess(eq("talent.read"), any());
  }

  @Test
  void rejectsAnEmptyOrReversedHalfOpenRange() {
    var at = Instant.parse("2026-09-01T00:00:00Z");
    assertThatThrownBy(() -> manager.intakes(null, null, null, at, at, null, 20))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("INTAKE_TIME_RANGE_INVALID"));
  }

  @Test
  void filtersConflictsByCurrentIntakeIdsWithinThePermissionScopedQuery() {
    when(persistence.findConflicts(
        eq("OPEN"), eq(List.of(INTAKE_A, INTAKE_B)), eq(null), eq("hr"), eq(false),
        eq(Set.of()), eq(Set.of()), eq(null), eq(null), eq(51)))
        .thenReturn(List.of());

    manager.conflicts("open", List.of(INTAKE_A, INTAKE_A, INTAKE_B), null, null, 50);

    verify(persistence).findConflicts(
        "OPEN", List.of(INTAKE_A, INTAKE_B), null, "hr", false, Set.of(), Set.of(),
        null, null, 51);
  }

  @Test
  void candidateConflictFilterPreservesScopeAndBindsThePaginationCursor() {
    String candidateId = "01JCANDIDATE00000000000000";
    String otherCandidateId = "01JCANDIDATE00000000000001";
    var createdAt = Instant.parse("2026-09-01T00:00:00Z");
    var conflict = new IdentityConflict();
    conflict.setId(INTAKE_A);
    conflict.setCreateTime(createdAt);
    ReflectionTestUtils.setField(manager, "cursorCodec",
        new SignedCursorCodec("candidate-conflict-disposable-test-key"));
    when(persistence.findConflicts("OPEN", List.of(INTAKE_A), candidateId, "hr", false,
        Set.of(), Set.of(), null, null, 2)).thenReturn(List.of(conflict, conflict));
    when(persistence.findConflicts("OPEN", List.of(INTAKE_A), candidateId, "hr", false,
        Set.of(), Set.of(), createdAt, INTAKE_A, 2)).thenReturn(List.of());

    var first = manager.conflicts("OPEN", List.of(INTAKE_A), candidateId, null, 1);
    assertThat(first.getItems()).containsExactly(conflict);
    assertThat(first.getNextCursor()).isNotBlank();
    manager.conflicts("OPEN", List.of(INTAKE_A), candidateId, first.getNextCursor(), 1);

    verify(persistence).findConflicts("OPEN", List.of(INTAKE_A), candidateId, "hr", false,
        Set.of(), Set.of(), createdAt, INTAKE_A, 2);
    assertThatThrownBy(() -> manager.conflicts("OPEN", List.of(INTAKE_A), otherCandidateId,
        first.getNextCursor(), 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
    assertThatThrownBy(() -> manager.conflicts("OPEN", List.of(INTAKE_A), null,
        first.getNextCursor(), 1))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CURSOR_INVALID"));
  }
}

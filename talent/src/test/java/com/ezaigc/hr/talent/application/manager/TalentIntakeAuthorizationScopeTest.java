package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.dto.ResolveIdentityConflictDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.IdentityConflict;
import com.ezaigc.hr.talent.domain.entity.IntakeBatch;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.response.IdentityConflictResponse;
import com.ezaigc.hr.talent.mapper.IdentityConflictMapper;
import com.ezaigc.hr.talent.mapper.TalentIntakeMapper;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.IdentityConflictPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TalentIntakeAuthorizationScopeTest {

  private final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();

  @Test
  void conflictPageLoadsDistinctCandidatesInOneBatchAndKeepsRedaction() {
    var candidates = mock(CandidatePersistenceService.class);
    var readAccess = mock(CandidateReadAccess.class);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "candidateReadAccess", readAccess);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    Candidate visible = new Candidate(); visible.setId("visible");
    Candidate hidden = new Candidate(); hidden.setId("hidden");
    when(candidates.listByIds(Set.of("visible", "hidden", "missing"))).thenReturn(List.of(hidden, visible));
    when(candidates.findCandidate("visible")).thenReturn(visible);
    when(candidates.findCandidateForUpdate("visible")).thenReturn(visible);
    when(candidates.findCandidate("hidden")).thenReturn(hidden);
    when(candidates.findCandidateForUpdate("hidden")).thenReturn(hidden);
    when(readAccess.evaluate(any())).thenReturn(new CandidateReadDecision(Set.of("visible"), Set.of(), Map.of()));
    IdentityConflict first = new IdentityConflict();
    first.setCandidateIdA("visible"); first.setCandidateIdB("hidden"); first.setChosenCandidateId("hidden");
    IdentityConflict second = new IdentityConflict();
    second.setCandidateIdA("visible"); second.setCandidateIdB("missing");

    CursorPage<IdentityConflictResponse> result = ReflectionTestUtils.invokeMethod(manager,
        "visibleConflictResponses", new CursorPage<>(List.of(first, second), "next", true));

    assertThat(result).isNotNull();
    assertThat(result.getNextCursor()).isEqualTo("next");
    assertThat(result.isHasMore()).isTrue();
    assertThat(result.getItems()).extracting(IdentityConflictResponse::getCandidateIdA).containsExactly("visible", "visible");
    assertThat(result.getItems()).extracting(IdentityConflictResponse::getCandidateIdB).containsOnlyNulls();
    assertThat(result.getItems().getFirst().getChosenCandidateId()).isNull();
    verify(candidates, times(1)).listByIds(Set.of("visible", "hidden", "missing"));
    verify(candidates, never()).findCandidate(any());
    verify(readAccess, times(1)).evaluate(List.of(hidden, visible));
  }

  @Test
  void conflictResponseHidesCandidateIdWithoutCandidateReadAccess() {
    var candidates = mock(CandidatePersistenceService.class);
    var readAccess = mock(CandidateReadAccess.class);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "candidateReadAccess", readAccess);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    Candidate hidden = new Candidate(); hidden.setId("hidden");
    Candidate visible = new Candidate(); visible.setId("visible");
    when(candidates.listByIds(Set.of("hidden", "visible"))).thenReturn(List.of(hidden, visible));
    when(readAccess.evaluate(List.of(hidden, visible)))
        .thenReturn(new CandidateReadDecision(Set.of("visible"), Set.of(), Map.of()));
    IdentityConflict conflict = new IdentityConflict();
    conflict.setId("conflict-1");
    conflict.setCandidateIdA("hidden");
    conflict.setCandidateIdB("visible");
    conflict.setChosenCandidateId("hidden");

    CursorPage<IdentityConflictResponse> response = ReflectionTestUtils.invokeMethod(manager,
        "visibleConflictResponses", new CursorPage<>(List.of(conflict), null, false));

    assertThat(response).isNotNull();
    assertThat(response.getItems().getFirst().getCandidateIdA()).isNull();
    assertThat(response.getItems().getFirst().getCandidateIdB()).isEqualTo("visible");
    assertThat(response.getItems().getFirst().getChosenCandidateId()).isNull();
    verify(readAccess).evaluate(List.of(hidden, visible));
  }

  @Test
  void hiddenCandidateCannotBeResolvedThroughDirectApiRequest() {
    var conflicts = mock(IdentityConflictPersistenceService.class);
    var intakes = mock(TalentIntakePersistenceService.class);
    var candidates = mock(CandidatePersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    ReflectionTestUtils.setField(manager, "identityConflictPersistenceService", conflicts);
    ReflectionTestUtils.setField(manager, "persistence", intakes);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    IdentityConflict conflict = new IdentityConflict();
    conflict.setIntakeId("intake-1");
    conflict.setCandidateIdA("hidden");
    when(conflicts.findConflict("conflict-1")).thenReturn(conflict);
    ResumeIntake intake = new ResumeIntake(); intake.setId("intake-1");
    when(intakes.findIntake("intake-1")).thenReturn(intake);
    Candidate hidden = new Candidate(); hidden.setId("hidden");
    when(candidates.findCandidate("hidden")).thenReturn(hidden);
    when(candidates.findCandidateForUpdate("hidden")).thenReturn(hidden);
    doAnswer(invocation -> {
      AuthorizationTarget target = invocation.getArgument(1);
      if ("CANDIDATE".equals(target.getObjectType())) {
        throw PlatformExceptions.forbidden("DATA_SCOPE_FORBIDDEN", "无权修改已有候选人");
      }
      return null;
    }).when(authorization).requireAccess(eq("talent.manage"), any(AuthorizationTarget.class));

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(manager, "resolveConflictCore",
        "conflict-1", new ResolveIdentityConflictDTO("CREATE_SEPARATE", null, "已人工核实")))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("DATA_SCOPE_FORBIDDEN"));
    verify(conflicts, never()).resolveConflict(any(), any(), any(), any(), any(), any());
  }

  @Test
  void companyWideHrDirectorCanAssignOutsideTheirOwnAffiliations() {
    var director = new CurrentActorSnapshot(
        "director", "人力负责人", "director@example.invalid", "home", Set.of(), Set.of(),
        Set.of(), Set.of("HR_DIRECTOR"), Set.of("talent.manage"), false, false);

    assertThatCode(() -> ReflectionTestUtils.invokeMethod(
        manager, "requireAssignable", director, "another-hr", "another-department"))
        .doesNotThrowAnyException();
  }

  @Test
  void sourceRecruiterIsAuditMetadataRatherThanAnImplicitBatchCollaborator() {
    var batch = new IntakeBatch();
    batch.setId("batch");
    batch.setDefaultOwnerUserId("new-owner");
    batch.setOwnerDepartmentId("department");
    batch.setSourceRecruiterUserId("original-uploader");

    AuthorizationTarget target = ReflectionTestUtils.invokeMethod(manager, "target", batch);

    assertThat(target).isNotNull();
    assertThat(target.getOwnerUserId()).isEqualTo("new-owner");
    assertThat(target.getCollaboratorUserIds()).isEmpty();
  }

  @Test
  void sourceRecruiterIsAuditMetadataRatherThanAnImplicitIntakeCollaborator() {
    var intake = new ResumeIntake();
    intake.setId("intake");
    intake.setOwnerUserId("new-owner");
    intake.setOwnerDepartmentId("department");
    intake.setSourceRecruiterUserId("original-uploader");

    AuthorizationTarget target = ReflectionTestUtils.invokeMethod(manager, "target", intake);

    assertThat(target).isNotNull();
    assertThat(target.getOwnerUserId()).isEqualTo("new-owner");
    assertThat(target.getCollaboratorUserIds()).isEmpty();
  }

  @Test
  void listAndConflictQueriesDoNotAuthorizeTheOriginalUploader() {
    assertThat(selectSql(TalentIntakeMapper.class, "findIntakes"))
        .doesNotContain("i.source_recruiter_user_id=#{actorId}");
    assertThat(selectSql(TalentIntakeMapper.class, "findConflicts"))
        .doesNotContain("i.source_recruiter_user_id=#{actorId}");
    assertThat(selectSql(IdentityConflictMapper.class, "countOpenByCandidateIds"))
        .doesNotContain("i.source_recruiter_user_id=#{actorId}");
  }

  private static String selectSql(Class<?> mapper, String methodName) {
    return Arrays.stream(mapper.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .findFirst()
        .orElseThrow()
        .getAnnotation(Select.class)
        .value()[0];
  }
}

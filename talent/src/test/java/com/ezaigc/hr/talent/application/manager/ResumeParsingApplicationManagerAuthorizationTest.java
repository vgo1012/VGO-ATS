package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.CorrectResumeFactsDTO;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeParsingApplicationManagerAuthorizationTest {

  private static final String VERSION_ID = "V".repeat(26);
  private static final String CANDIDATE_ID = "C".repeat(26);
  private static final String OWNER_ID = "O".repeat(26);
  private static final String DEPARTMENT_ID = "D".repeat(26);

  private final ResumeParsingPersistenceService persistence =
      mock(ResumeParsingPersistenceService.class);
  private final ExtractionRunPersistenceService extractionRuns =
      mock(ExtractionRunPersistenceService.class);
  private final ResumeExtractionPagePersistenceService extractionPages =
      mock(ResumeExtractionPagePersistenceService.class);
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final ResumeParsingApplicationManager manager = new ResumeParsingApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "library", library);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "extractionRunPersistenceService", extractionRuns);
    ReflectionTestUtils.setField(
        manager, "resumeExtractionPagePersistenceService", extractionPages);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
    ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
    when(candidates.lockCandidate(CANDIDATE_ID)).thenReturn(CANDIDATE_ID);
    when(versions.lockForRouting(VERSION_ID)).thenReturn(VERSION_ID);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "resumeVersions", versions);
    when(persistence.findResumeResourceForUpdate(VERSION_ID, CANDIDATE_ID))
        .thenReturn(new ResumeResourceEntity(VERSION_ID, "resume.pdf", "application/pdf", "sha256",
            "resume/object", CANDIDATE_ID, OWNER_ID, DEPARTMENT_ID));
    when(persistence.findResumeResource(VERSION_ID))
        .thenReturn(
            new ResumeResourceEntity(
                VERSION_ID,
                "resume.pdf",
                "application/pdf",
                "sha256",
                "resume/object",
                CANDIDATE_ID,
                OWNER_ID,
                DEPARTMENT_ID));
  }

  @Test
  void allowsFactCorrectionWithReadAccessWithoutDedicatedCorrectionPermission() {
    doThrow(forbidden()).when(authorization).requireAccess(eq("talent.fact.correct"), any());

    // 通过同一对象的查看校验后，仍须满足快照已生成等业务前置条件。
    assertThatThrownBy(() -> manager.correct(correction()))
        .isInstanceOf(PlatformException.class)
        .hasMessage("简历事实尚未生成");

    verify(authorization).requireAccess(eq("talent.read"), argThat(target ->
        "CANDIDATE".equals(target.getObjectType())
            && CANDIDATE_ID.equals(target.getObjectId())
            && OWNER_ID.equals(target.getOwnerUserId())
            && DEPARTMENT_ID.equals(target.getDepartmentId())));
    verify(authorization, never()).requireAccess(eq("talent.fact.correct"), any());
    verify(persistence).findCurrentFactSnapshotForUpdate(VERSION_ID);
  }

  @Test
  void rejectsFactCorrectionWithoutReadAccessBeforeLoadingSensitiveFacts() {
    PlatformException forbidden = forbidden();
    rejectRead(forbidden);

    assertThatThrownBy(() -> manager.correct(correction())).isSameAs(forbidden);

    verify(persistence, never()).findCurrentFactSnapshot(VERSION_ID);
    verify(persistence, never()).findCurrentFactSnapshotForUpdate(VERSION_ID);
  }

  @Test
  void rejectsReparseBeforeEnqueueWhenCurrentFactsContainHumanCorrection() {
    var current = new FactSnapshot();
    current.setSourceType("HUMAN_CORRECTION");
    when(persistence.findCurrentFactSnapshot(VERSION_ID)).thenReturn(current);

    assertThatThrownBy(() -> manager.enqueue(VERSION_ID, "retry"))
        .isInstanceOf(PlatformException.class)
        .hasMessage("当前事实已人工更正，请继续人工更正，或上传新简历版本")
        .satisfies(error -> assertThat(((PlatformException) error).code())
            .isEqualTo("HUMAN_CORRECTION_CURRENT"));
  }

  private CorrectResumeFactsDTO correction() {
    return new CorrectResumeFactsDTO(VERSION_ID, "S".repeat(26), "已与候选人核实",
        mock(ResumeFacts.class));
  }

  @Test
  void rejectsOutOfScopeFactReadsBeforeLoadingSensitiveFacts() {
    PlatformException forbidden = forbidden();
    rejectRead(forbidden);

    assertThatThrownBy(() -> manager.facts(VERSION_ID)).isSameAs(forbidden);

    verify(persistence, never()).findCurrentFactSnapshot(VERSION_ID);
  }

  @Test
  void sharedFactsAndHistoricalFactsIgnoreOwnerScopeButRevokeAfterLeavingLibrary() {
    var denied = forbidden();
    rejectRead(denied);
    when(library.eligibleCandidateIds(List.of(CANDIDATE_ID))).thenReturn(Set.of(CANDIDATE_ID));
    assertThatThrownBy(() -> manager.facts(VERSION_ID))
        .isInstanceOf(PlatformException.class).hasMessage("简历事实尚未生成");
    assertThatThrownBy(() -> manager.historicalFacts(VERSION_ID, "old-snapshot"))
        .isInstanceOf(PlatformException.class).hasMessage("该简历版本的事实快照不存在");
    verify(persistence).findCurrentFactSnapshot(VERSION_ID);
    verify(persistence).findFactSnapshot("old-snapshot");
    when(library.eligibleCandidateIds(List.of(CANDIDATE_ID))).thenReturn(Set.of());
    assertThatThrownBy(() -> manager.facts(VERSION_ID)).isSameAs(denied);
  }

  @Test
  void sharedReadDoesNotGrantFactCorrection() {
    var denied = forbidden();
    rejectRead(denied);
    when(library.eligibleCandidateIds(List.of(CANDIDATE_ID))).thenReturn(Set.of(CANDIDATE_ID));
    assertThatThrownBy(() -> manager.correct(correction())).isSameAs(denied);
    verify(persistence, never()).findCurrentFactSnapshot(VERSION_ID);
    verify(persistence, never()).findCurrentFactSnapshotForUpdate(VERSION_ID);
  }

  private void rejectRead(PlatformException forbidden) {
    doThrow(forbidden)
        .when(authorization)
        .requireAccess(
            eq("talent.read"),
            argThat(
                target ->
                    "CANDIDATE".equals(target.getObjectType())
                        && CANDIDATE_ID.equals(target.getObjectId())
                        && OWNER_ID.equals(target.getOwnerUserId())
                        && DEPARTMENT_ID.equals(target.getDepartmentId())));
  }

  private static PlatformException forbidden() {
    return new PlatformException(
        HttpStatus.FORBIDDEN, "OBJECT_SCOPE_FORBIDDEN", "对象不在当前用户可访问范围", false);
  }
}

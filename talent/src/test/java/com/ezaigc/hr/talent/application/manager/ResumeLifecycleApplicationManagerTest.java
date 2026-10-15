package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.api.CandidateRecruitmentStateQuery;
import com.ezaigc.hr.talent.api.CandidateResumeVersionService;
import com.ezaigc.hr.talent.api.CandidateScreeningStateQuery;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.domain.dto.WorkCalendarCalculationDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.entity.TalentProcessEvent;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import tools.jackson.databind.ObjectMapper;

class ResumeLifecycleApplicationManagerTest {
  private static final Instant NOW = Instant.parse("2026-09-20T17:00:00Z");
  private static final LocalDate SETTLEMENT = LocalDate.of(2026, 9, 20);
  private AutoCloseable mocks;
  @Mock private ResumeLifecyclePersistenceService cycles;
  @Mock private CandidatePersistenceService candidates;
  @Mock private TalentIntakePersistenceService resumes;
  @Mock private CandidateResumeVersionService routing;
  @Mock private CandidateRecruitmentStateQuery recruitment;
  @Mock private CandidateScreeningStateQuery screening;
  @Mock private WorkCalendarCalculationManager calendar;
  @Mock private TalentProcessEventPersistenceService events;
  @Mock private ResumeSearchProjectionRecorder searchProjection;
  @Mock private ResumeLifecycleProperties properties;
  @Mock private PlatformIdGenerator ids;
  @Mock private Clock clock;
  @Spy private ObjectMapper json = new ObjectMapper();
  @InjectMocks private ResumeLifecycleApplicationManager manager;
  private ResumeLifecycle row;
  private Candidate candidate;

  @BeforeEach
  void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "lifecycle"),
        ResumeLifecycle.class);
    when(properties.isEnabled()).thenReturn(true);
    when(clock.instant()).thenReturn(NOW);
    when(ids.newId()).thenReturn("new-cycle", "event");
    when(cycles.update(any())).thenReturn(true);
    candidate = new Candidate();
    candidate.setId("candidate"); candidate.setStatus("ACTIVE"); candidate.setOwnerUserId("owner");
    when(candidates.findCandidate("candidate")).thenReturn(candidate);
    var resume = new CandidateResumeRow();
    resume.setCandidateId("candidate"); resume.setResumeVersionId("resume");
    when(resumes.findCurrentCandidateResumes(List.of("candidate"))).thenReturn(List.of(resume));
    when(screening.activeResumeVersionIds(List.of("candidate"))).thenReturn(Set.of());
    when(recruitment.states(List.of("candidate"))).thenReturn(Map.of());
    row = new ResumeLifecycle();
    row.setId("cycle"); row.setCandidateId("candidate"); row.setResumeVersionId("resume");
    row.setEnteredAt(Instant.parse("2026-09-14T02:00:00Z"));
    row.setPhase("PENDING_SCREENING"); row.setWarningLevel("NORMAL");
    row.setActiveMarker("Y"); row.setVersion(7);
    when(cycles.getById("cycle")).thenReturn(row);
    when(cycles.lockCurrent("resume")).thenReturn(row);
  }

  @AfterEach
  void tearDown() throws Exception { mocks.close(); }

  @ParameterizedTest
  @CsvSource({"0,PENDING_SCREENING,NORMAL", "1,PENDING_DISPOSAL,NORMAL",
      "2,PENDING_DISPOSAL,YELLOW", "3,PENDING_DISPOSAL,RED", "4,SHARED,NORMAL"})
  void appliesCalculatedStageWithVersionedPersistence(int days, String phase, String warning) {
    calculated(days);
    assertThat(manager.advance("cycle", SETTLEMENT, false))
        .isEqualTo(days == 4 ? "SHARED" : "SUCCEEDED");
    var update = persistedFields();
    assertThat(update).containsEntry("phase", phase).containsEntry("warning_level", warning)
        .containsEntry("elapsed_workdays", days).containsEntry("version", "version + 1");
    if (days == 4) {
      assertThat(update).containsEntry("shared_at", NOW).containsEntry("next_check_at", null);
      assertChangedAudit("AUTO_TIMEOUT");
    }
    verify(routing).lockForRouting("candidate", "resume");
  }

  @Test
  void missingCalendarPreservesCurrentStageAndSchedulesRecovery() {
    row.setPhase("PENDING_DISPOSAL"); row.setWarningLevel("YELLOW"); row.setElapsedWorkdays(2);
    when(calendar.calculate(any(), eq(SETTLEMENT), eq(true))).thenReturn(
        new WorkCalendarCalculationDTO(0, Map.of(), List.of(2026), List.of(), false));
    assertThat(manager.advance("cycle", SETTLEMENT, false)).isEqualTo("BLOCKED");
    assertThat(persistedFields()).containsEntry("phase", "PENDING_DISPOSAL")
        .containsEntry("warning_level", "YELLOW").containsEntry("elapsed_workdays", 2)
        .containsEntry("pause_reason", "CALENDAR_MISSING");
    assertThat(row.getNextCheckAt()).isAfter(NOW);
    verify(events, never()).save(any());
  }

  @Test
  void missingNextYearKeepsCompletedSettlementButSchedulesCalendarRecovery() {
    var yearEnd = LocalDate.of(2026, 12, 31);
    row.setEnteredAt(Instant.parse("2026-12-28T00:00:00Z"));
    when(calendar.calculate(any(), eq(yearEnd), eq(true))).thenReturn(new WorkCalendarCalculationDTO(
        3, Map.of(2026, "published"), List.of(2027), List.of(LocalDate.of(2026, 12, 29),
        LocalDate.of(2026, 12, 30), yearEnd), true));
    assertThat(manager.advance("cycle", yearEnd, false)).isEqualTo("SUCCEEDED");
    assertThat(persistedFields()).containsEntry("elapsed_workdays", 3)
        .containsEntry("phase", "PENDING_DISPOSAL").containsEntry("warning_level", "RED")
        .containsEntry("pause_reason", "CALENDAR_MISSING").containsEntry("share_due_at", null);
    assertThat(row.getNextCheckAt()).isAfter(NOW);
  }

  @Test
  void calendarCorrectionCanLowerWarningAndRecordsItsOwnReason() {
    row.setPhase("PENDING_DISPOSAL"); row.setWarningLevel("RED"); row.setElapsedWorkdays(3);
    calculated(0);
    assertThat(manager.advance("cycle", SETTLEMENT, true)).isEqualTo("SUCCEEDED");
    assertThat(persistedFields()).containsEntry("phase", "PENDING_SCREENING")
        .containsEntry("warning_level", "NORMAL").containsEntry("elapsed_workdays", 0);
    assertChangedAudit("CALENDAR_CORRECTION");
  }

  @Test
  void calendarPublicationDoesNotImmediatelyEscalateOrShare() {
    row.setPauseReason("CALENDAR_MISSING");
    calculated(4);
    assertThat(manager.advance("cycle", SETTLEMENT, true)).isEqualTo("DEFERRED");
    assertThat(persistedFields()).containsEntry("phase", "PENDING_SCREENING")
        .containsEntry("warning_level", "NORMAL").containsEntry("elapsed_workdays", 4)
        .containsEntry("pause_reason", null).containsEntry("shared_at", null);
    assertThat(row.getShareDueAt()).isNotNull();
    verify(events, never()).save(any());
    verify(searchProjection, never()).candidateChanged(any(), any(), any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"DELETED", "VERSION_CHANGED", "SCREENING", "ACTIVE_RECRUITMENT", "OTHER_ACTIVE", "MANUAL_SHARED"})
  void authoritativeRoutingFactsPreventAutomaticSharing(String blocker) {
    switch (blocker) {
      case "DELETED" -> candidate.setStatus("DELETED");
      case "VERSION_CHANGED" -> when(resumes.findCurrentCandidateResumes(List.of("candidate"))).thenReturn(List.of());
      case "SCREENING" -> when(screening.activeResumeVersionIds(List.of("candidate"))).thenReturn(Set.of("resume"));
      case "ACTIVE_RECRUITMENT" -> when(recruitment.hasActiveRecruitment("resume")).thenReturn(true);
      case "OTHER_ACTIVE" -> when(recruitment.states(List.of("candidate"))).thenReturn(Map.of("candidate",
          List.of(new CandidateRecruitmentStateQuery.State("older-resume", "owner", "IN_PROCESS", true, false))));
      case "MANUAL_SHARED" -> candidate.setExt("{\"screenedOutResumeVersionId\":\"resume\"}");
      default -> throw new IllegalArgumentException(blocker);
    }
    assertThat(manager.advance("cycle", SETTLEMENT, false)).isEqualTo("CLOSED");
    assertThat(persistedFields()).containsEntry("phase", "CLOSED").containsEntry("active_marker", null);
    verify(calendar, never()).calculate(any(), any(), anyBoolean());
  }

  @ParameterizedTest
  @ValueSource(strings = {"SCREENING", "ACTIVE_RECRUITMENT"})
  void anotherCurrentLogicalResumeBlocksAutomaticSharing(String blocker) {
    var current = new CandidateResumeRow();
    current.setCandidateId("candidate"); current.setResumeVersionId("resume");
    var other = new CandidateResumeRow();
    other.setCandidateId("candidate"); other.setResumeVersionId("other-current-resume");
    when(resumes.findCurrentCandidateResumes(List.of("candidate"))).thenReturn(List.of(current, other));
    if ("SCREENING".equals(blocker)) {
      when(screening.activeResumeVersionIds(List.of("candidate"))).thenReturn(Set.of("other-current-resume"));
    } else {
      when(recruitment.hasActiveRecruitment("other-current-resume")).thenReturn(true);
    }
    assertThat(manager.advance("cycle", SETTLEMENT, false)).isEqualTo("CLOSED");
    assertThat(persistedFields()).containsEntry("phase", "CLOSED").containsEntry("active_marker", null);
    verify(calendar, never()).calculate(any(), any(), anyBoolean());
    verify(searchProjection, never()).candidateChanged(any(), any(), any(), any(), any());
  }

  @Test
  void repeatedInitializationAndOwnerChangeDoNotRestartTheClock() {
    when(cycles.lastCycle("resume")).thenReturn(1);
    manager.initialize("candidate", "resume", NOW);
    candidate.setOwnerUserId("another-owner");
    manager.initialize("candidate", "resume", NOW.plusSeconds(3600));
    verify(cycles, never()).save(any());
    verify(cycles, never()).update(any());
  }

  @Test
  void newIntakeStartsAtActualAdmissionAndEmitsAnEntryAudit() {
    manager.entered("candidate", "resume");
    var inserted = ArgumentCaptor.forClass(ResumeLifecycle.class);
    verify(cycles).save(inserted.capture());
    assertThat(inserted.getValue().getEnteredAt()).isEqualTo(NOW);
    assertThat(inserted.getValue().getCycleNo()).isEqualTo(1);
    assertThat(inserted.getValue().getNextCheckAt())
        .isEqualTo(Instant.parse("2026-09-22T17:00:00Z"));
    verify(events).insertEvent(anyString(), eq("RESUME_LIFECYCLE_ENTERED"), eq(SYSTEM_ACTOR_ID),
        eq("CANDIDATE"), eq("candidate"), contains("resumeVersion=resume"), eq(NOW));
  }

  @Test
  void disabledFeatureDoesNotCreateOrCloseExistingFacts() {
    when(properties.isEnabled()).thenReturn(false);
    manager.initialize("candidate", "resume", NOW);
    manager.entered("candidate", "resume");
    manager.returned("candidate", "resume");
    manager.routed("candidate", "resume", "SCREENING");
    manager.reclaimed("candidate", "resume");
    verifyNoInteractions(cycles, routing, candidates, resumes, recruitment, screening,
        calendar, events, searchProjection);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void disabledFeatureNeverAdvancesExistingOverdueCycles(boolean correctionOnly) {
    when(properties.isEnabled()).thenReturn(false);
    row.setPhase("PENDING_DISPOSAL"); row.setWarningLevel("RED"); row.setElapsedWorkdays(3);
    calculated(4);
    assertThat(manager.advance("cycle", SETTLEMENT, correctionOnly)).isEqualTo("SKIPPED");
    verifyNoInteractions(cycles, routing, candidates, resumes, recruitment, screening,
        calendar, events, searchProjection);
    assertThat(row.getPhase()).isEqualTo("PENDING_DISPOSAL");
    assertThat(row.getSharedAt()).isNull();
  }

  @Test
  void successfulManualRoutingClosesOnlyTheCurrentCycle() {
    manager.routed("candidate", "resume", "SCREENING");
    assertThat(persistedFields()).containsEntry("phase", "CLOSED")
        .containsEntry("active_marker", null).containsEntry("next_check_at", null)
        .containsEntry("closed_reason", "SCREENING");
    verify(events).insertEvent(anyString(), eq("RESUME_LIFECYCLE_CLOSED"), eq(SYSTEM_ACTOR_ID),
        eq("CANDIDATE"), eq("candidate"), contains("reason=SCREENING"), eq(NOW));
  }

  @Test
  void returnWaitsUntilAllJdAdmissionsHaveLeftTheScreeningQueue() {
    when(screening.activeResumeVersionIds(List.of("candidate"))).thenReturn(Set.of("resume"));
    manager.returned("candidate", "resume");
    verify(cycles, never()).save(any());
    when(screening.activeResumeVersionIds(List.of("candidate"))).thenReturn(Set.of());
    when(cycles.lockCurrent("resume")).thenReturn(null);
    when(cycles.lastCycle("resume")).thenReturn(1);
    manager.returned("candidate", "resume");
    var inserted = ArgumentCaptor.forClass(ResumeLifecycle.class);
    verify(cycles).save(inserted.capture());
    assertThat(inserted.getValue().getCycleNo()).isEqualTo(2);
    assertThat(inserted.getValue().getEnteredAt()).isEqualTo(NOW);
    assertThat(inserted.getValue().getPhase()).isEqualTo("PENDING_SCREENING");
  }

  @Test
  void alreadySharedCycleIsNotSharedOrAuditedTwice() {
    row.setPhase("SHARED");
    assertThat(manager.advance("cycle", SETTLEMENT, false)).isEqualTo("SKIPPED");
    verify(cycles, never()).update(any());
    verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
    verify(events, never()).save(any());
  }

  @Test
  void historicalRunRetryCannotDowngradeAMoreRecentlySettledCycle() {
    row.setSettledThrough(SETTLEMENT);
    row.setPhase("PENDING_DISPOSAL"); row.setWarningLevel("RED"); row.setElapsedWorkdays(3);
    assertThat(manager.advance("cycle", SETTLEMENT.minusDays(2), false)).isEqualTo("SKIPPED");
    verify(cycles, never()).update(any());
    verify(calendar, never()).calculate(any(), any(), anyBoolean());
    verify(events, never()).save(any());
    assertThat(row.getWarningLevel()).isEqualTo("RED");
    assertThat(row.getElapsedWorkdays()).isEqualTo(3);
  }

  @Test
  void autoSharingThatWinsTheVersionLockRejectsLateScreeningAdmission() {
    row.setPhase("SHARED");
    assertThatThrownBy(() -> manager.routed("candidate", "resume", "SCREENING_ADMITTED"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("RESUME_ALREADY_AUTO_SHARED"));
    verify(routing).lockForRouting("candidate", "resume");
    verify(cycles, never()).update(any());
    verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
    verify(events, never()).save(any());
  }

  @Test
  void recruitmentFromSharedLibraryPreservesTheOriginalSharedAdmission() {
    row.setPhase("SHARED");
    manager.routed("candidate", "resume", "RECRUITMENT_CREATED");
    verify(cycles, never()).update(any());
  }

  @Test
  void failedCompareAndSwapDoesNotAppendSuccessfulAudit() {
    calculated(4);
    when(cycles.update(any())).thenReturn(false);
    assertThatThrownBy(() -> manager.advance("cycle", SETTLEMENT, false)).isInstanceOf(RuntimeException.class);
    verify(events, never()).insertEvent(any(), any(), any(), any(), any(), any(), any());
    verify(events, never()).save(any());
  }

  @Test
  void calendarPublishedAfterJobClaimCannotEscalateInsideTheLockedTransition() {
    Instant publishedAt=NOW.plusSeconds(60);
    when(clock.instant()).thenReturn(NOW.plusSeconds(120));
    calculatedWithPublication(4,SETTLEMENT,publishedAt);
    // Job 已按旧代次领取且传 false，事务内的新版本时间仍阻止提前共享。
    assertThat(manager.advance("cycle",SETTLEMENT,false)).isEqualTo("DEFERRED");
    assertThat(persistedFields()).containsEntry("phase","PENDING_SCREENING")
        .containsEntry("elapsed_workdays",4).containsEntry("shared_at",null);
    verify(searchProjection,never()).candidateChanged(any(),any(),any(),any(),any());
    verify(events,never()).save(any());
  }

  @Test
  void freshlyPublishedCalendarStillAllowsImmediateDowngradeWithCorrectionAudit() {
    row.setPhase("PENDING_DISPOSAL");row.setWarningLevel("RED");row.setElapsedWorkdays(3);
    when(clock.instant()).thenReturn(NOW.plusSeconds(120));
    calculatedWithPublication(0,SETTLEMENT,NOW.plusSeconds(60));
    assertThat(manager.advance("cycle",SETTLEMENT,false)).isEqualTo("SUCCEEDED");
    assertThat(persistedFields()).containsEntry("phase","PENDING_SCREENING")
        .containsEntry("warning_level","NORMAL");
    assertChangedAudit("CALENDAR_CORRECTION");
  }

  @Test
  void publicationAtTheNormalBoundaryIsAlreadyEffectiveForThatBoundary() {
    calculatedWithPublication(4,SETTLEMENT,NOW);
    assertThat(manager.advance("cycle",SETTLEMENT,false)).isEqualTo("SHARED");
    assertThat(persistedFields()).containsEntry("phase","SHARED").containsEntry("shared_at",NOW);
  }

  @Test
  void nextNormalBoundaryCanApplyPreviouslyDeferredPublication() {
    LocalDate nextBoundary=SETTLEMENT.plusDays(1);
    when(clock.instant()).thenReturn(NOW.plusSeconds(86400));
    calculatedWithPublication(4,nextBoundary,NOW.plusSeconds(60));
    assertThat(manager.advance("cycle",nextBoundary,false)).isEqualTo("SHARED");
    assertThat(persistedFields()).containsEntry("phase","SHARED")
        .containsEntry("shared_at",NOW.plusSeconds(86400));
  }

  private void calculatedWithPublication(int days,LocalDate settlement,Instant publishedAt) {
    when(calendar.calculate(any(),eq(settlement),eq(true))).thenReturn(new WorkCalendarCalculationDTO(
        days,Map.of(2026,"published"),List.of(),List.of(LocalDate.of(2026,9,15),
        LocalDate.of(2026,9,16),LocalDate.of(2026,9,17),LocalDate.of(2026,9,18)),true,publishedAt));
  }

  private void calculated(int days) {
    when(calendar.calculate(any(), eq(SETTLEMENT), eq(true))).thenReturn(new WorkCalendarCalculationDTO(
        days, Map.of(2026, "published"), List.of(), List.of(LocalDate.of(2026, 9, 15),
        LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18)), true));
  }

  private void assertChangedAudit(String reason) {
    var captured = ArgumentCaptor.forClass(TalentProcessEvent.class);
    verify(events).save(captured.capture());
    var event = captured.getValue();
    assertThat(event.getEventType()).isEqualTo("RESUME_LIFECYCLE_CHANGED");
    assertThat(event.getActorUserId()).isEqualTo(SYSTEM_ACTOR_ID);
    assertThat(event.getTargetId()).isEqualTo("candidate");
    assertThat(event.getReason()).contains(reason).hasSizeLessThanOrEqualTo(500);
    var detail = json.readTree(event.getExt());
    assertThat(detail.path("cycleId").asString()).isEqualTo("cycle");
    assertThat(detail.path("settlementDate").asString()).isEqualTo(SETTLEMENT.toString());
    assertThat(detail.path("ownerUserId").asString()).isEqualTo("owner");
    assertThat(detail.path("calendarRevisions").isMissingNode()).isFalse();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> persistedFields() {
    var capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(cycles).update(capture.capture());
    LambdaUpdateWrapper<ResumeLifecycle> wrapper = capture.getValue();
    Map<String, Object> fields = new LinkedHashMap<>();
    for (String assignment : wrapper.getSqlSet().split(",")) {
      int separator = assignment.indexOf('=');
      String key = assignment.substring(0, separator).trim();
      if (!assignment.contains("MPGENVAL")) {
        fields.put(key, assignment.substring(separator + 1).trim());
        continue;
      }
      String parameter = assignment.substring(assignment.indexOf("MPGENVAL"), assignment.indexOf('}'));
      fields.put(key, wrapper.getParamNameValuePairs().get(parameter));
    }
    return fields;
  }
}

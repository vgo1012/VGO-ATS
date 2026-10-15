package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_FEEDBACK_VERSIONS_PER_BATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.recruiting.interview.domain.entity.Feedback;
import com.ezaigc.hr.recruiting.interview.domain.entity.FeedbackVersion;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.mapper.FeedbackVersionMapper;
import com.ezaigc.hr.recruiting.interview.service.FeedbackVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewFeedbackPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.impl.FeedbackVersionPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OwnershipHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.StageHistory;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.RecruitmentOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.StageHistoryPersistenceService;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateRecruitmentHistoryApplicationManagerTest {
  @BeforeEach
  void initializeHistoryTableMetadata() {
    var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "history-batch-test");
    for (Class<?> type : List.of(Interview.class, Feedback.class, FeedbackVersion.class,
        StageHistory.class, OwnershipHistory.class)) {
      TableInfoHelper.initTableInfo(assistant, type);
    }
  }

  @Test
  void returnsOldOwnerInterviewAndSubmittedFeedbackFactsAfterCandidateReassignment() {
    var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "candidate-history");
    TableInfoHelper.initTableInfo(assistant, Interview.class);
    TableInfoHelper.initTableInfo(assistant, Feedback.class);
    var manager = new CandidateRecruitmentHistoryApplicationManager();
    var candidateAccess = mock(CandidateAccessPolicy.class);
    var applications = mock(ApplicationPersistenceService.class);
    var stages = mock(StageHistoryPersistenceService.class);
    var ownerships = mock(RecruitmentOwnershipHistoryPersistenceService.class);
    var interviews = mock(InterviewPersistenceService.class);
    var feedbacks = mock(InterviewFeedbackPersistenceService.class);
    var feedbackVersions = mock(FeedbackVersionPersistenceService.class);
    var application = new Application();
    application.setId("application");
    application.setCandidateId("candidate");
    application.setJdId("jd");
    application.setJdVersionId("jd-version");
    application.setResumeVersionId("resume");
    application.setRecruitmentType("INTERNAL");
    application.setCurrentStage("TERMINATED");
    application.setOutcome("REJECTED");
    application.setOwnerUserId("later-owner");
    application.setCreateTime(Instant.parse("2026-08-01T01:00:00Z"));
    application.setTerminalAt(Instant.parse("2026-08-03T01:00:00Z"));
    when(applications.findForCandidates(List.of("candidate"))).thenReturn(List.of(application));
    var stage = new StageHistory();
    stage.setApplicationId("application");
    stage.setFromStage("SCREENING");
    stage.setToStage("INTERVIEW");
    stage.setOperatedByUserId("operator");
    stage.setOccurredAt(Instant.parse("2026-08-02T01:00:00Z"));
    when(stages.findStageHistory("application")).thenReturn(List.of(stage));
    when(stages.list(any(Wrapper.class))).thenReturn(List.of(stage));
    var originalAssignment = new OwnershipHistory();
    originalAssignment.setApplicationId("application");
    originalAssignment.setActionType("ASSIGN");
    originalAssignment.setToUserId("original-owner");
    originalAssignment.setOperatedByUserId("operator");
    originalAssignment.setCreateTime(Instant.parse("2026-08-01T01:00:00Z"));
    when(ownerships.findOwnershipHistory("application")).thenReturn(List.of(originalAssignment));
    when(ownerships.list(any(Wrapper.class))).thenReturn(List.of(originalAssignment));
    var interview = new Interview();
    interview.setId("interview");
    interview.setApplicationId("application");
    interview.setRoundNo(1);
    interview.setInterviewType("TECHNICAL");
    interview.setStatus("COMPLETED");
    interview.setScheduledStartAt(Instant.parse("2026-08-02T02:00:00Z"));
    interview.setScheduledEndAt(Instant.parse("2026-08-02T03:00:00Z"));
    interview.setOwnerUserId("interviewer");
    when(interviews.list(any(Wrapper.class))).thenReturn(List.of(interview));
    var feedback = new Feedback();
    feedback.setId("feedback");
    feedback.setInterviewId("interview");
    feedback.setAuthorUserId("feedback-author");
    feedback.setFeedbackKind("INTERVIEWER");
    feedback.setStatus("SUBMITTED");
    feedback.setCurrentVersionNo(2);
    when(feedbacks.list(any(Wrapper.class))).thenReturn(List.of(feedback));
    var feedbackVersion = new FeedbackVersion();
    feedbackVersion.setFeedbackId("feedback");
    feedbackVersion.setVersionNo(2);
    feedbackVersion.setVerdict("PASS");
    feedbackVersion.setShortComment("通过");
    feedbackVersion.setFeedbackOccurredAt(Instant.parse("2026-08-02T04:00:00Z"));
    when(feedbackVersions.findFeedbackVersions(Map.of("feedback", 2)))
        .thenReturn(Map.of("feedback", feedbackVersion));
    ReflectionTestUtils.setField(manager, "candidateAccess", candidateAccess);
    ReflectionTestUtils.setField(manager, "applications", applications);
    ReflectionTestUtils.setField(manager, "stages", stages);
    ReflectionTestUtils.setField(manager, "ownerships", ownerships);
    ReflectionTestUtils.setField(manager, "interviews", interviews);
    ReflectionTestUtils.setField(manager, "feedbacks", feedbacks);
    ReflectionTestUtils.setField(manager, "feedbackVersions", feedbackVersions);

    var result = manager.history("candidate");

    verify(candidateAccess).requireAllResumesReadable("candidate");
    assertThat(result.getItems()).singleElement().satisfies(history -> {
      assertThat(history.getOriginalOwnerUserId()).isEqualTo("original-owner");
      assertThat(history.getCurrentOwnerUserId()).isEqualTo("later-owner");
      assertThat(history.getStageEvents()).singleElement()
          .satisfies(event -> assertThat(event.getToStage()).isEqualTo("INTERVIEW"));
      assertThat(history.getInterviews()).singleElement().satisfies(historyInterview -> {
        assertThat(historyInterview.getStatus()).isEqualTo("COMPLETED");
        assertThat(historyInterview.getFeedbacks()).singleElement().satisfies(historyFeedback -> {
          assertThat(historyFeedback.getAuthorUserId()).isEqualTo("feedback-author");
          assertThat(historyFeedback.getFeedbackOccurredAt())
              .isEqualTo(Instant.parse("2026-08-02T04:00:00Z"));
        });
      });
    });
  }

  @Test
  void loadsTenApplicationHistoriesWithOneQueryForEachHistorySource() {
    var rows = IntStream.range(0, 10)
        .mapToObj(index -> application("application-" + index, "owner-" + index, index)).toList();
    Fixture fixture = new Fixture(rows);
    fixture.histories(List.of(), List.of());

    assertThat(fixture.manager.history("candidate").getItems()).hasSize(10);

    verify(fixture.stages).list(any(Wrapper.class));
    verify(fixture.ownerships).list(any(Wrapper.class));
    verify(fixture.stages, never()).findStageHistory(anyString());
    verify(fixture.ownerships, never()).findOwnershipHistory(anyString());
    verifyNoMoreInteractions(fixture.stages, fixture.ownerships);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void bindsOnlyVisibleApplicationIdsAndPreservesTimeThenIdOrdering() {
    Fixture fixture = new Fixture(List.of(application("application-a", "owner-a", 1),
        application("application-b", "owner-b", 2)));
    fixture.histories(List.of(), List.of());

    fixture.manager.history("candidate");

    ArgumentCaptor<LambdaQueryWrapper<StageHistory>> stageQuery =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    ArgumentCaptor<LambdaQueryWrapper<OwnershipHistory>> ownershipQuery =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(fixture.stages).list(stageQuery.capture());
    verify(fixture.ownerships).list(ownershipQuery.capture());
    assertThat(stageQuery.getValue().getSqlSegment())
        .contains("application_id IN", "ORDER BY occurred_at ASC,id ASC");
    assertThat(ownershipQuery.getValue().getSqlSegment())
        .contains("application_id IN", "ORDER BY create_time ASC,id ASC");
    assertThat(stageQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("application-a", "application-b");
    assertThat(ownershipQuery.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("application-a", "application-b");
  }

  @Test
  void groupsHistoryByApplicationAndPreservesEqualTimeEventAndApplicationOrder() {
    Fixture fixture = new Fixture(List.of(application("application-a", "owner-a", 1),
        application("application-b", "owner-b", 2)));
    fixture.histories(
        List.of(stage("a-1", "application-a", "SCREENING"),
            stage("a-2", "application-a", "INTERVIEW"), stage("b-1", "application-b", "CONTACT")),
        List.of(ownership("a-1", "application-a", "original-a"),
            ownership("a-2", "application-a", "owner-a"), ownership("b-1", "application-b", "original-b")));

    var result = fixture.manager.history("candidate");

    assertThat(result.getItems()).extracting(value -> value.getApplicationId())
        .containsExactly("application-b", "application-a");
    assertThat(result.getItems().getFirst().getOriginalOwnerUserId()).isEqualTo("original-b");
    assertThat(result.getItems().getFirst().getStageEvents())
        .extracting(value -> value.getToStage()).containsExactly("CONTACT");
    var historyA = result.getItems().getLast();
    assertThat(historyA.getOriginalOwnerUserId()).isEqualTo("original-a");
    assertThat(historyA.getCurrentOwnerUserId()).isEqualTo("owner-a");
    assertThat(historyA.getStageEvents()).extracting(value -> value.getToStage())
        .containsExactly("SCREENING", "INTERVIEW");
    assertThat(historyA.getOwnershipEvents()).extracting(value -> value.getToUserId())
        .containsExactly("original-a", "owner-a");
  }

  @Test
  void fallsBackToCurrentOwnerWhenAnApplicationHasNoOwnershipHistory() {
    Fixture fixture = new Fixture(List.of(application("application-a", "owner-a", 1),
        application("application-b", "owner-b", 2)));
    fixture.histories(List.of(), List.of(ownership("b-1", "application-b", "original-b")));

    var result = fixture.manager.history("candidate");

    var historyA = result.getItems().getLast();
    assertThat(historyA.getOriginalOwnerUserId()).isEqualTo("owner-a");
    assertThat(historyA.getStageEvents()).isEmpty();
    assertThat(historyA.getOwnershipEvents()).isEmpty();
    assertThat(result.getItems().getFirst().getOriginalOwnerUserId()).isEqualTo("original-b");
  }

  @Test
  void returnsEmptyWithoutHistoryReadsWhenCandidateHasNoApplications() {
    Fixture fixture = new Fixture(List.of());

    assertThat(fixture.manager.history("candidate").getItems()).isEmpty();

    verify(fixture.candidateAccess).requireAllResumesReadable("candidate");
    verify(fixture.applications).findForCandidates(List.of("candidate"));
    verifyNoMoreInteractions(fixture.applications);
    verifyNoInteractions(fixture.stages, fixture.ownerships, fixture.interviews,
        fixture.feedbacks, fixture.feedbackVersions);
  }

  @Test
  void rejectsUnauthorizedCandidateBeforeAnyDatabaseRead() {
    Fixture fixture = new Fixture(List.of(application("application-a", "owner-a", 1)));
    PlatformException denied = new PlatformException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权限", false);
    doThrow(denied).when(fixture.candidateAccess).requireAllResumesReadable("candidate");

    assertThatThrownBy(() -> fixture.manager.history("candidate")).isSameAs(denied);

    verifyNoInteractions(fixture.applications, fixture.stages, fixture.ownerships,
        fixture.interviews, fixture.feedbacks, fixture.feedbackVersions);
  }

  @ParameterizedTest(name = "{0} submitted feedbacks with real capped version service")
  @ValueSource(ints = {0, 1, 200, 201, 401})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void readsCompleteFeedbackHistoryAcrossTheRealPersistenceBatchLimit(int size) {
    Fixture fixture = new Fixture(List.of(application("application-a", "owner-a", 1)));
    fixture.histories(List.of(), List.of());
    Interview first = new Interview();
    first.setId("interview-a");
    first.setApplicationId("application-a");
    Interview second = new Interview();
    second.setId("interview-b");
    second.setApplicationId("application-a");
    when(fixture.interviews.list(any(Wrapper.class))).thenReturn(List.of(first, second));
    List<Feedback> submitted = IntStream.range(0, size).mapToObj(index -> {
      Feedback feedback = new Feedback();
      feedback.setId(String.format(Locale.ROOT, "%026d", index));
      feedback.setInterviewId(index % 2 == 0 ? "interview-a" : "interview-b");
      feedback.setAuthorUserId("author-" + index);
      feedback.setFeedbackKind("INTERVIEWER");
      feedback.setStatus("SUBMITTED");
      feedback.setCurrentVersionNo(index % 3 + 1);
      return feedback;
    }).toList();
    when(fixture.feedbacks.list(any(Wrapper.class))).thenReturn(submitted);
    FeedbackVersionMapper mapper = mock(FeedbackVersionMapper.class);
    FeedbackVersionPersistenceServiceImpl cappedService = new FeedbackVersionPersistenceServiceImpl();
    ReflectionTestUtils.setField(cappedService, "baseMapper", mapper);
    ReflectionTestUtils.setField(fixture.manager, "feedbackVersions", cappedService);
    String missingId = size > MAX_FEEDBACK_VERSIONS_PER_BATCH ? submitted.getLast().getId() : null;
    when(mapper.selectList(any())).thenAnswer(invocation -> {
      LambdaQueryWrapper<FeedbackVersion> query = invocation.getArgument(0);
      return requestedVersions(query).entrySet().stream()
          .filter(entry -> !entry.getKey().equals(missingId))
          .map(entry -> feedbackVersion(entry.getKey(), entry.getValue())).toList();
    });

    var result = assertDoesNotThrow(() -> fixture.manager.history("candidate"));

    assertThat(result.getItems()).hasSize(1);
    var interviews = result.getItems().getFirst().getInterviews();
    assertThat(interviews).extracting(value -> value.getInterviewId())
        .containsExactly("interview-a", "interview-b");
    for (var interview : interviews) {
      List<Feedback> expected = submitted.stream()
          .filter(feedback -> interview.getInterviewId().equals(feedback.getInterviewId())).toList();
      assertThat(interview.getFeedbacks()).extracting(value -> value.getFeedbackId())
          .containsExactlyElementsOf(expected.stream().map(Feedback::getId).toList());
      for (int index = 0; index < expected.size(); index++) {
        Feedback feedback = expected.get(index);
        var actual = interview.getFeedbacks().get(index);
        assertThat(actual.getAuthorUserId()).isEqualTo(feedback.getAuthorUserId());
        assertThat(actual.getFeedbackKind()).isEqualTo(feedback.getFeedbackKind());
        if (feedback.getId().equals(missingId)) {
          assertThat(actual.getVerdict()).isNull();
          assertThat(actual.getShortComment()).isNull();
          assertThat(actual.getFeedbackOccurredAt()).isNull();
        } else {
          FeedbackVersion version = feedbackVersion(feedback.getId(), feedback.getCurrentVersionNo());
          assertThat(actual.getVerdict()).isEqualTo(version.getVerdict());
          assertThat(actual.getShortComment()).isEqualTo(version.getShortComment());
          assertThat(actual.getFeedbackOccurredAt()).isEqualTo(version.getFeedbackOccurredAt());
        }
      }
    }
    ArgumentCaptor<LambdaQueryWrapper<FeedbackVersion>> captor =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    int batches = (size + MAX_FEEDBACK_VERSIONS_PER_BATCH - 1) / MAX_FEEDBACK_VERSIONS_PER_BATCH;
    verify(mapper, times(batches)).selectList(captor.capture());
    Map<String, Integer> allRequested = new LinkedHashMap<>();
    for (var query : captor.getAllValues()) {
      Map<String, Integer> requested = requestedVersions(query);
      assertThat(requested.size()).isBetween(1, MAX_FEEDBACK_VERSIONS_PER_BATCH);
      assertThat(allRequested.keySet()).doesNotContainAnyElementsOf(requested.keySet());
      allRequested.putAll(requested);
    }
    Map<String, Integer> expectedVersions = new LinkedHashMap<>();
    submitted.forEach(feedback -> expectedVersions.put(feedback.getId(), feedback.getCurrentVersionNo()));
    assertThat(allRequested).isEqualTo(expectedVersions);
    verifyNoMoreInteractions(mapper);
    verify(fixture.stages).list(any(Wrapper.class));
    verify(fixture.ownerships).list(any(Wrapper.class));
    verify(fixture.candidateAccess).requireAllResumesReadable("candidate");
  }

  private static Map<String, Integer> requestedVersions(LambdaQueryWrapper<FeedbackVersion> query) {
    String sql = query.getSqlSegment();
    Pattern pairPattern = Pattern.compile(
        "\\(feedback_id = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\} AND version_no = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}\\)");
    Matcher pairs = pairPattern.matcher(sql);
    Map<String, Object> parameters = query.getParamNameValuePairs();
    Map<String, Integer> requested = new LinkedHashMap<>();
    while (pairs.find()) {
      String id = (String) parameters.get(pairs.group(1));
      assertThat(requested).doesNotContainKey(id);
      requested.put(id, (Integer) parameters.get(pairs.group(2)));
    }
    assertThat(parameters).hasSize(requested.size() * 2);
    return requested;
  }

  private static FeedbackVersion feedbackVersion(String id, int versionNo) {
    FeedbackVersion result = new FeedbackVersion();
    result.setFeedbackId(id);
    result.setVersionNo(versionNo);
    result.setVerdict(versionNo % 2 == 0 ? "PASS" : "REJECT");
    result.setShortComment("comment-" + id + "-v" + versionNo);
    result.setFeedbackOccurredAt(Instant.parse("2026-10-03T01:00:00Z").plusSeconds(versionNo));
    return result;
  }

  private static Application application(String id, String owner, int day) {
    Application result = new Application();
    result.setId(id);
    result.setCandidateId("candidate");
    result.setOwnerUserId(owner);
    result.setCreateTime(Instant.parse("2026-10-01T01:00:00Z").plusSeconds(day * 86400L));
    return result;
  }

  private static StageHistory stage(String id, String applicationId, String toStage) {
    StageHistory result = new StageHistory();
    result.setId(id);
    result.setApplicationId(applicationId);
    result.setToStage(toStage);
    result.setOccurredAt(Instant.parse("2026-10-03T01:00:00Z"));
    return result;
  }

  private static OwnershipHistory ownership(String id, String applicationId, String toUser) {
    OwnershipHistory result = new OwnershipHistory();
    result.setId(id);
    result.setApplicationId(applicationId);
    result.setToUserId(toUser);
    result.setCreateTime(Instant.parse("2026-10-03T01:00:00Z"));
    return result;
  }

  @SuppressWarnings("unchecked")
  private static class Fixture {
    private final CandidateRecruitmentHistoryApplicationManager manager =
        new CandidateRecruitmentHistoryApplicationManager();
    private final CandidateAccessPolicy candidateAccess = mock(CandidateAccessPolicy.class);
    private final ApplicationPersistenceService applications = mock(ApplicationPersistenceService.class);
    private final StageHistoryPersistenceService stages = mock(StageHistoryPersistenceService.class);
    private final RecruitmentOwnershipHistoryPersistenceService ownerships =
        mock(RecruitmentOwnershipHistoryPersistenceService.class);
    private final InterviewPersistenceService interviews = mock(InterviewPersistenceService.class);
    private final InterviewFeedbackPersistenceService feedbacks = mock(InterviewFeedbackPersistenceService.class);
    private final FeedbackVersionPersistenceService feedbackVersions = mock(FeedbackVersionPersistenceService.class);
    private final List<Application> rows;

    private Fixture(List<Application> rows) {
      this.rows = rows;
      when(applications.findForCandidates(List.of("candidate"))).thenReturn(rows);
      when(interviews.list(any(Wrapper.class))).thenReturn(List.of());
      when(feedbackVersions.findFeedbackVersions(Map.of())).thenReturn(Map.of());
      ReflectionTestUtils.setField(manager, "candidateAccess", candidateAccess);
      ReflectionTestUtils.setField(manager, "applications", applications);
      ReflectionTestUtils.setField(manager, "stages", stages);
      ReflectionTestUtils.setField(manager, "ownerships", ownerships);
      ReflectionTestUtils.setField(manager, "interviews", interviews);
      ReflectionTestUtils.setField(manager, "feedbacks", feedbacks);
      ReflectionTestUtils.setField(manager, "feedbackVersions", feedbackVersions);
    }

    private void histories(List<StageHistory> stageRows, List<OwnershipHistory> ownershipRows) {
      when(stages.list(any(Wrapper.class))).thenReturn(stageRows);
      when(ownerships.list(any(Wrapper.class))).thenReturn(ownershipRows);
      for (Application application : rows) {
        when(stages.findStageHistory(application.getId())).thenReturn(stageRows.stream()
            .filter(row -> application.getId().equals(row.getApplicationId())).toList());
        when(ownerships.findOwnershipHistory(application.getId())).thenReturn(ownershipRows.stream()
            .filter(row -> application.getId().equals(row.getApplicationId())).toList());
      }
    }
  }
}

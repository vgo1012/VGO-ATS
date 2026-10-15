package com.ezaigc.hr.recruiting.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.SUBMISSION_WITHDRAWAL_PAGE_SIZE;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.APPLICATION_TERMINATION_PAGE_SIZE;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewMapper;
import com.ezaigc.hr.recruiting.interview.service.impl.InterviewPersistenceServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentMatchers;

class InterviewPersistenceServiceImplTest {
  private final InterviewMapper mapper = mock(InterviewMapper.class);
  private final InterviewPersistenceServiceImpl service = new InterviewPersistenceServiceImpl();

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Interview.class);
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.selectList(any())).thenReturn(List.of());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void currentTypeBatchUsesLatestActualLabelsAndSkipsCancelledSchedules() {
    var latest = new Interview(); latest.setWorkflowStepId("step"); latest.setInterviewType("EXTERNAL");
    var older = new Interview(); older.setWorkflowStepId("step"); older.setInterviewType("INTERNAL");
    when(mapper.selectList(any())).thenReturn(List.of(latest, older));
    assertThat(service.latestTypesByStepIds(Set.of("step"))).containsEntry("step", "EXTERNAL");
    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getSqlSegment()).contains("workflow_step_id IN", "status IN", "ORDER BY create_time DESC,id DESC");
    assertThat(captor.getValue().getParamNameValuePairs().values()).contains("step", "SCHEDULED", "COMPLETED").doesNotContain("CANCELLED");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void activeRoundBatchKeepsOnlyScheduledRoundsGroupedByApplication() {
    var roundFive = interview("application-a", 5, "SCHEDULED");
    var duplicateRoundThree = interview("application-a", 3, "SCHEDULED");
    var roundThree = interview("application-a", 3, "SCHEDULED");
    var otherApplication = interview("application-b", 2, "SCHEDULED");
    var completed = interview("application-a", 1, "COMPLETED");
    var cancelled = interview("application-a", 4, "CANCELLED");
    when(mapper.selectList(any())).thenReturn(
        List.of(roundFive, duplicateRoundThree, roundThree, otherApplication, completed, cancelled));

    assertThat(service.activeRoundNosByApplicationIds(Set.of("application-a", "application-b")))
        .containsExactlyInAnyOrderEntriesOf(Map.of(
            "application-a", List.of(3, 5),
            "application-b", List.of(2)));

    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getSqlSegment())
        .contains("application_id IN", "status =", "ORDER BY application_id ASC,round_no ASC,id ASC");
    assertThat(captor.getValue().getParamNameValuePairs().values())
        .contains("application-a", "application-b", "SCHEDULED")
        .doesNotContain("COMPLETED", "CANCELLED");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void completedRoundBatchKeepsTheHighestCompletedRoundPerApplication() {
    when(mapper.selectList(any())).thenReturn(List.of(
        interview("application-a", 3, "COMPLETED"),
        interview("application-a", 1, "COMPLETED"),
        interview("application-b", 2, "COMPLETED")));

    assertThat(service.latestCompletedRoundNosByApplicationIds(Set.of("application-a", "application-b")))
        .containsExactlyInAnyOrderEntriesOf(Map.of("application-a", 3, "application-b", 2));

    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getSqlSegment())
        .contains("application_id IN", "status =", "ORDER BY application_id ASC,round_no DESC,id DESC");
    assertThat(captor.getValue().getParamNameValuePairs().values())
        .contains("application-a", "application-b", "COMPLETED")
        .doesNotContain("SCHEDULED", "CANCELLED");
  }

  private static Interview interview(String applicationId, int roundNo, String status) {
    var interview = new Interview();
    interview.setApplicationId(applicationId);
    interview.setRoundNo(roundNo);
    interview.setStatus(status);
    return interview;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings({"unchecked", "rawtypes"})
  void withdrawalUsesAnExactBoundedCurrentReadAndSkipsCancelledHistory(boolean nextPage) {
    String afterId = nextPage ? "I".repeat(26) : null;
    service.lockSubmissionInterviews("A".repeat(26), "S".repeat(26), afterId);

    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    var query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("application_id =", "client_submission_id =", "status IN",
        "ORDER BY id ASC", "LIMIT " + SUBMISSION_WITHDRAWAL_PAGE_SIZE + " FOR UPDATE");
    assertThat(query.getParamNameValuePairs().values())
        .contains("A".repeat(26), "S".repeat(26), "SCHEDULED", "COMPLETED").doesNotContain("CANCELLED");
    if (afterId != null) {
      assertThat(query.getSqlSegment()).contains("id >");
      assertThat(query.getParamNameValuePairs().values()).contains(afterId);
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void completionReadsLatestNonCancelledScheduleRegardlessOfTypeLabel() {
    service.lockLatestByStep("T".repeat(26));
    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(captor.capture(), ArgumentMatchers.eq(true));
    var query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("workflow_step_id =", "status IN",
        "ORDER BY create_time DESC,id DESC", "LIMIT 1 FOR UPDATE");
    assertThat(query.getParamNameValuePairs().values())
        .contains("T".repeat(26), "SCHEDULED", "COMPLETED").doesNotContain("CANCELLED");
    assertThat(query.getSqlSegment()).doesNotContain("interview_type =");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void duplicateScheduleCheckLocksTheCurrentActiveInterview() {
    service.lockScheduledByStep("T".repeat(26));

    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(captor.capture(), ArgumentMatchers.eq(true));
    var query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("workflow_step_id =", "status =", "LIMIT 1 FOR UPDATE");
    assertThat(query.getParamNameValuePairs().values()).contains("T".repeat(26), "SCHEDULED");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings({"unchecked", "rawtypes"})
  void applicationTerminationLocksOnlyScheduledInterviewsInBoundedPages(boolean nextPage) {
    String applicationId = "A".repeat(26), afterId = nextPage ? "I".repeat(26) : null;
    service.lockScheduledApplicationInterviews(applicationId, afterId);

    ArgumentCaptor<LambdaQueryWrapper<Interview>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    var query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("application_id =", "status =", "ORDER BY id ASC",
        "LIMIT " + APPLICATION_TERMINATION_PAGE_SIZE + " FOR UPDATE").doesNotContain("client_submission_id");
    assertThat(query.getParamNameValuePairs().values())
        .contains(applicationId, "SCHEDULED").doesNotContain("COMPLETED", "CANCELLED");
    if (nextPage) {
      assertThat(query.getSqlSegment()).contains("id >");
      assertThat(query.getParamNameValuePairs().values()).contains(afterId);
    }
  }
}

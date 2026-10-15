package com.ezaigc.hr.recruiting.recruitment.service;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.WorkflowStep;
import com.ezaigc.hr.recruiting.recruitment.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.RecruitmentWorkflowStepPersistenceServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.IntStream;

class RecruitmentWorkflowStepPersistenceServiceImplTest {

  private static final String APPLICATION_ID = "A".repeat(26);
  private static final String TEMPLATE_ID = "W".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void findsActiveStepsForAWholeApplicationPageInWorkflowOrder() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        WorkflowStep.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service =
        spy(new RecruitmentWorkflowStepPersistenceServiceImpl());
    ArgumentCaptor<LambdaQueryWrapper<WorkflowStep>> captor =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    doReturn(List.of()).when(service).list(captor.capture());
    List<String> applicationIds = List.of(APPLICATION_ID, "B".repeat(26));

    assertThat(service.findActiveSteps(applicationIds)).isEmpty();

    LambdaQueryWrapper<WorkflowStep> query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("application_id IN", "status IN", "ORDER BY",
        "application_id", "sequence_no", "id");
    assertThat(query.getParamNameValuePairs().values())
        .contains(APPLICATION_ID, "B".repeat(26), "PENDING", "IN_PROGRESS")
        .doesNotContain("PASSED", "FAILED", "WITHDRAWN");
  }

  @Test
  @SuppressWarnings("unchecked")
  void emptyActiveStepBatchDoesNotQueryStorage() {
    RecruitmentWorkflowStepPersistenceServiceImpl service =
        spy(new RecruitmentWorkflowStepPersistenceServiceImpl());

    assertThat(service.findActiveSteps(List.of())).isEmpty();
    assertThat(service.findActiveSteps(null)).isEmpty();

    verify(service, never()).list(any(Wrapper.class));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void bindingRequiresAnUnboundActiveStepAndTheExpectedVersion() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), WorkflowStep.class);
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<WorkflowStep>> captor = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), captor.capture())).thenReturn(1);

    assertThat(service.bindClientSubmission("T".repeat(26), "S".repeat(26), 7L, "B".repeat(26), NOW)).isOne();

    var update = captor.getValue();
    assertThat(update.getSqlSegment()).contains("id =", "status IN", "client_submission_id IS NULL", "version =");
    assertThat(update.getParamNameValuePairs().values()).contains("PENDING", "IN_PROGRESS", 7L)
        .doesNotContain("PASSED", "FAILED", "WITHDRAWN");
    assertThat(update.getSqlSet()).contains("client_submission_id", "version=version + 1", "update_id", "update_time")
        .doesNotContain("result_reason", "completed_at", "started_at");
  }

  @Test
  void writesTheCompleteApplicationPathInOneBoundedBatch() {
    RecruitmentWorkflowStepPersistenceServiceImpl service =
        spy(new RecruitmentWorkflowStepPersistenceServiceImpl());
    List<WorkflowStep> steps =
        List.of(
            step("S".repeat(26), "F".repeat(26), "CANDIDATE_COMMUNICATION", 1),
            step("T".repeat(26), "G".repeat(26), "DECISION", 2));
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));

    assertThat(service.insertSteps(steps)).isTrue();

    verify(service).saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  @Test
  void rejectsEmptyOversizedAndCrossApplicationBatches() {
    RecruitmentWorkflowStepPersistenceServiceImpl service =
        new RecruitmentWorkflowStepPersistenceServiceImpl();
    List<WorkflowStep> oversized =
        IntStream.rangeClosed(1, MAX_WORKFLOW_STEP_COUNT + 1)
            .mapToObj(
                sequence ->
                    step(
                        String.format("%026d", sequence),
                        String.format("%026d", sequence + 100),
                        "DECISION",
                        sequence))
            .toList();
    WorkflowStep otherApplication = step("T".repeat(26), "G".repeat(26), "DECISION", 2);
    otherApplication.setApplicationId("B".repeat(26));

    assertInvalid(service, List.of());
    assertInvalid(service, oversized);
    assertInvalid(
        service,
        List.of(
            step("S".repeat(26), "F".repeat(26), "CANDIDATE_COMMUNICATION", 1),
            otherApplication));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void terminalWithdrawalUpdatesOnlyActiveOrdinaryStepsAndReturnsActualRowCount() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        WorkflowStep.class);
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<WorkflowStep>> captor = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), captor.capture())).thenReturn(2);

    assertThat(service.withdrawActiveSteps(APPLICATION_ID, "人工终止", "B".repeat(26), NOW)).isEqualTo(2);
    LambdaUpdateWrapper<WorkflowStep> update = captor.getValue();
    assertThat(update.getSqlSegment()).contains("application_id =", "status IN", "step_code NOT IN");
    assertThat(update.getSqlSet()).contains("status", "result_reason", "completed_at", "version");
    assertThat(update.getParamNameValuePairs().values())
        .contains(APPLICATION_ID, "PENDING", "IN_PROGRESS", "WITHDRAWN", "DECISION", "OFFER", "人工终止", NOW)
        .doesNotContain("PASSED", "FAILED", "SKIPPED", "NOT_SELECTED", "AVAILABLE");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @SuppressWarnings({"rawtypes", "unchecked"})
  void selectionBatchScopesUnstartedRowsAndPreservesHistoricalFields(boolean selected) {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        WorkflowStep.class);
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<WorkflowStep>> captor = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), captor.capture())).thenReturn(2);
    List<String> ids = List.of("S".repeat(26), "T".repeat(26));

    assertThat(service.updateUnstartedSelection(APPLICATION_ID, ids, selected, "B".repeat(26), NOW)).isEqualTo(2);

    LambdaUpdateWrapper<WorkflowStep> update = captor.getValue();
    assertThat(update.getSqlSegment()).contains("application_id =", "id IN", "status IN",
        "started_at IS NULL", "completed_at IS NULL");
    assertThat(update.getSqlSet()).contains("status", "owner_user_id", "due_at", "version", "update_id", "update_time")
        .doesNotContain("started_at", "completed_at", "result_reason", "client_submission_id");
    assertThat(update.getParamNameValuePairs().values())
        .contains(APPLICATION_ID, ids.getFirst(), ids.getLast(), "AVAILABLE", "NOT_SELECTED", "PENDING", NOW)
        .doesNotContain("IN_PROGRESS", "WITHDRAWN", "PASSED", "FAILED", "SKIPPED");
  }

  @Test
  void emptySelectionDoesNotReachTheMapperAndUnsafeBatchesAreRejected() {
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    String id = "S".repeat(26);
    assertThat(service.updateUnstartedSelection(APPLICATION_ID, List.of(), true, APPLICATION_ID, NOW)).isZero();
    assertThatThrownBy(() -> service.updateUnstartedSelection(null, List.of(id), true, APPLICATION_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateUnstartedSelection(APPLICATION_ID, List.of(id), true, "", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateUnstartedSelection(APPLICATION_ID, List.of(id), true, APPLICATION_ID, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateUnstartedSelection(APPLICATION_ID, null, true, APPLICATION_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    for (List<String> ids : List.of(List.of(id, id), List.of("invalid"),
        Arrays.asList(id, null), Collections.nCopies(MAX_WORKFLOW_STEP_COUNT + 1, id))) {
      assertThatThrownBy(() -> service.updateUnstartedSelection(APPLICATION_ID, ids, true, APPLICATION_ID, NOW))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("application path selection batch is invalid");
    }
    verifyNoInteractions(mapper);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void clientWithdrawalCannotRewriteCompletedHistory() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        WorkflowStep.class);
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<WorkflowStep>> captor = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), captor.capture())).thenReturn(1);

    assertThat(service.withdrawBoundClientStep("S".repeat(26), "撤回当前步骤", "B".repeat(26), NOW)).isEqualTo(1);
    LambdaUpdateWrapper<WorkflowStep> update = captor.getValue();
    assertThat(update.getSqlSegment()).contains("id =", "status IN");
    assertThat(update.getParamNameValuePairs().values())
        .contains("PENDING", "IN_PROGRESS", "WITHDRAWN", "撤回当前步骤", NOW)
        .doesNotContain("PASSED", "FAILED", "SKIPPED", "NOT_SELECTED", "AVAILABLE");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void controlledOfferReopenRestoresSubsequentOfferWithoutRewritingFacts() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        WorkflowStep.class);
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    RecruitmentWorkflowStepPersistenceServiceImpl service = new RecruitmentWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<WorkflowStep>> captor = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(isNull(), captor.capture())).thenReturn(1);

    int changed = service.activateOfferStep(
        APPLICATION_ID, "B".repeat(26), NOW, "B".repeat(26), NOW);

    assertThat(changed).isEqualTo(1);
    LambdaUpdateWrapper<WorkflowStep> update = captor.getValue();
    assertThat(update.getSqlSegment()).contains("application_id =", "step_code =", "status IN");
    assertThat(update.getSqlSet()).contains("started_at", "completed_at", "result_reason", "version");
    assertThat(update.getParamNameValuePairs().values())
        .contains(APPLICATION_ID, "OFFER", "FAILED", "WITHDRAWN", "IN_PROGRESS", NOW)
        .doesNotContain("PENDING", "SKIPPED");
    assertThat(update.getParamNameValuePairs().values())
        .contains("AVAILABLE", "NOT_SELECTED")
        .doesNotContain("PASSED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"CANDIDATE_COMMUNICATION", "PRE_SUBMISSION_INTERNAL_INTERVIEW", "CLIENT_SUBMISSION",
      "CLIENT_AI_INTERVIEW", "INTERVIEW_ROUND_1", "INTERVIEW_ROUND_2", "INTERVIEW_ROUND_3", "DECISION", "OFFER"})
  void acceptsEveryCanonicalStepCodeWithoutChangingTheBoundedWrite(String code) {
    var service = spy(new RecruitmentWorkflowStepPersistenceServiceImpl());
    var steps = List.of(step("S".repeat(26), "F".repeat(26), code, 1));
    doReturn(true).when(service).saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));
    assertThat(service.insertSteps(steps)).isTrue();
    verify(service).saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "decision", " DECISION ", "UNKNOWN"})
  void rejectsNoncanonicalStepCodesBeforeWriting(String code) {
    var service = spy(new RecruitmentWorkflowStepPersistenceServiceImpl());
    assertInvalid(service, List.of(step("S".repeat(26), "F".repeat(26), code, 1)));
    verify(service, never()).saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));
  }

  private static void assertInvalid(
      RecruitmentWorkflowStepPersistenceServiceImpl service, List<WorkflowStep> steps) {
    assertThatThrownBy(() -> service.insertSteps(steps))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("application workflow step batch is invalid");
  }

  private static WorkflowStep step(
      String id, String definitionId, String stepCode, int sequenceNo) {
    WorkflowStep step = new WorkflowStep();
    step.setId(id);
    step.setApplicationId(APPLICATION_ID);
    step.setTemplateVersionId(TEMPLATE_ID);
    step.setStepDefinitionId(definitionId);
    step.setStepCode(stepCode);
    step.setSequenceNo(sequenceNo);
    step.setCandidateOptional(!"DECISION".equals(stepCode));
    step.setDefaultFollowUpChannel(
        "CANDIDATE_COMMUNICATION".equals(stepCode) ? "PHONE" : null);
    step.setStatus("AVAILABLE");
    step.setVersion(0L);
    step.setCreateTime(NOW);
    step.setUpdateTime(NOW);
    return step;
  }
}

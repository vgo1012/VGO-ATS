package com.ezaigc.hr.recruiting.demand.service;

import static com.ezaigc.hr.recruiting.demand.constant.DemandConstants.MAX_WORKFLOW_STEP_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.demand.domain.entity.WorkflowStepEntity;
import com.ezaigc.hr.recruiting.demand.mapper.WorkflowStepMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.DemandWorkflowStepPersistenceServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DemandWorkflowStepPersistenceServiceImplTest {

  private static final String TEMPLATE_ID = "W".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @BeforeAll
  static void initializeMetadata() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "workflow-step-test"),
        WorkflowStepEntity.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void batchReadFiltersByTemplateIdsAndUsesStableTemplateStepOrder() {
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    DemandWorkflowStepPersistenceServiceImpl service =
        new DemandWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    WorkflowStepEntity result = step("S".repeat(26), "DECISION", 1, false);
    String otherTemplateId = "V".repeat(26);
    doReturn(List.of(result)).when(mapper).selectList(any());

    assertThat(service.findWorkflowStepsByTemplateIds(List.of(TEMPLATE_ID, otherTemplateId)))
        .containsExactly(result);

    ArgumentCaptor<LambdaQueryWrapper<WorkflowStepEntity>> query =
        ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(query.capture());
    assertThat(query.getValue().getSqlSegment())
        .contains(
            "template_version_id IN",
            "ORDER BY template_version_id ASC,sequence_no ASC,id ASC");
    assertThat(query.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder(TEMPLATE_ID, otherTemplateId);
  }

  @Test
  void emptyBatchReadDoesNotQueryPersistence() {
    WorkflowStepMapper mapper = mock(WorkflowStepMapper.class);
    DemandWorkflowStepPersistenceServiceImpl service =
        new DemandWorkflowStepPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);

    assertThat(service.findWorkflowStepsByTemplateIds(List.of())).isEmpty();

    verifyNoInteractions(mapper);
  }

  @Test
  void writesTheCompleteValidatedTemplateInOneBoundedBatch() {
    DemandWorkflowStepPersistenceServiceImpl service =
        spy(new DemandWorkflowStepPersistenceServiceImpl());
    List<WorkflowStepEntity> steps =
        List.of(
            step("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, true),
            step("T".repeat(26), "DECISION", 2, false));
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));

    assertThat(service.insertWorkflowSteps(steps)).isTrue();

    verify(service).saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  @Test
  void rejectsEmptyOversizedAndInternallyInconsistentBatches() {
    DemandWorkflowStepPersistenceServiceImpl service =
        new DemandWorkflowStepPersistenceServiceImpl();
    List<WorkflowStepEntity> oversized =
        IntStream.rangeClosed(1, MAX_WORKFLOW_STEP_COUNT + 1)
            .mapToObj(
                sequence ->
                    step(
                        String.format("%026d", sequence),
                        "DECISION",
                        sequence,
                        false))
            .toList();
    WorkflowStepEntity duplicateSequence = step("U".repeat(26), "OFFER", 1, false);

    assertInvalid(service, List.of());
    assertInvalid(service, oversized);
    assertInvalid(
        service,
        List.of(
            step("S".repeat(26), "CANDIDATE_COMMUNICATION", 1, true),
            duplicateSequence));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CANDIDATE_COMMUNICATION", "PRE_SUBMISSION_INTERNAL_INTERVIEW", "CLIENT_SUBMISSION",
      "CLIENT_AI_INTERVIEW", "INTERVIEW_ROUND_1", "INTERVIEW_ROUND_2", "INTERVIEW_ROUND_3", "DECISION", "OFFER"})
  void acceptsEveryCanonicalStepCodeWithoutChangingTheBoundedWrite(String code) {
    var service = spy(new DemandWorkflowStepPersistenceServiceImpl());
    var steps = List.of(step("S".repeat(26), code, 1, false));
    doReturn(true).when(service).saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));
    assertThat(service.insertWorkflowSteps(steps)).isTrue();
    verify(service).saveBatch(steps, MAX_WORKFLOW_STEP_COUNT);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "decision", " DECISION ", "UNKNOWN"})
  void rejectsNoncanonicalStepCodesBeforeWriting(String code) {
    var service = spy(new DemandWorkflowStepPersistenceServiceImpl());
    assertInvalid(service, List.of(step("S".repeat(26), code, 1, false)));
    verify(service, never()).saveBatch(anyCollection(), eq(MAX_WORKFLOW_STEP_COUNT));
  }

  private static void assertInvalid(
      DemandWorkflowStepPersistenceServiceImpl service, List<WorkflowStepEntity> steps) {
    assertThatThrownBy(() -> service.insertWorkflowSteps(steps))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("workflow step batch is invalid");
  }

  private static WorkflowStepEntity step(
      String id, String stepCode, int sequenceNo, boolean optional) {
    WorkflowStepEntity step = new WorkflowStepEntity();
    step.setId(id);
    step.setTemplateVersionId(TEMPLATE_ID);
    step.setStepCode(stepCode);
    step.setSequenceNo(sequenceNo);
    step.setCandidateOptional(optional);
    step.setDefaultFollowUpChannel(
        "CANDIDATE_COMMUNICATION".equals(stepCode) ? "PHONE" : null);
    step.setCreateTime(NOW);
    return step;
  }
}

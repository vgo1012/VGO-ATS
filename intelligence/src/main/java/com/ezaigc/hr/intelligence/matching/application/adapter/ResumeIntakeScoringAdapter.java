package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.BackgroundActorExecutor;
import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRun;
import com.ezaigc.hr.intelligence.matching.domain.entity.SelectionItem;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunPurpose;
import com.ezaigc.hr.intelligence.matching.service.SelectionItemPersistenceService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 使用请求人的实时权限与个人评分设置，把自动导入接入现有评分任务。 */
@Component
public class ResumeIntakeScoringAdapter implements ResumeIntakeScoringService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResumeIntakeScoringAdapter.class);
  private static final ObjectMapper FACT_JSON = JsonMapper.builder()
      .changeDefaultVisibility(visibility -> visibility
          .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();
  @Resource private BackgroundActorExecutor actors;
  @Resource private MatchRunApplicationManager matching;
  @Resource private AiMatchingEvaluationManager evaluations;
  @Resource private ResumeFactQueryService resumeFacts;
  @Resource private SelectionItemPersistenceService selectionItems;
  @Resource private AsyncTaskPort tasks;
  @Resource private ObjectMapper json;
  @Resource private Clock clock;

  @Override
  public ScoringTaskRef enqueue(String jdVersionId, String resumeVersionId, String requestedByUserId) {
    return actors.execute(requestedByUserId, () -> enqueue(jdVersionId, resumeVersionId));
  }

  @Override
  public ScoringTaskRef enqueueRescore(
      String jdVersionId, String resumeVersionId, String requestedByUserId, String operationKey) {
    return actors.execute(
        requestedByUserId,
        () -> reference(matching.createForRescore(jdVersionId, resumeVersionId, operationKey)));
  }

  @Override
  public String scoredFactSnapshotId(
      String runId, String jdVersionId, String resumeVersionId, String requestedByUserId) {
    return actors.execute(requestedByUserId, () -> {
      MatchRun run = matching.get(runId);
      if (!jdVersionId.equals(run.getJdVersionId())
          || !MatchRunPurpose.COMPANY_DEFAULT.matches(run.getRunPurpose())) {
        throw PlatformExceptions.conflict("INTAKE_SCORE_RUN_MISMATCH", "原公司评分与当前收件不匹配");
      }
      List<SelectionItem> items = selectionItems.findSelectionItems(run.getSelectionSnapshotId());
      if (items == null || items.size() != 1
          || !resumeVersionId.equals(items.getFirst().getResumeVersionId())) {
        throw PlatformExceptions.conflict("INTAKE_SCORE_RUN_MISMATCH", "原公司评分与当前收件不匹配");
      }
      return items.getFirst().getResumeFactSnapshotId();
    });
  }

  @Override
  public ScoringTaskRef enqueue(String jdVersionId, String resumeVersionId,
      String requestedByUserId, PrecomputedAssessment assessment) {
    return actors.execute(requestedByUserId, () -> {
      try {
        var jd = matching.requireIntakeScoringReady(jdVersionId);
        var resume = resumeFacts.currentFactBatch(List.of(resumeVersionId)).get(resumeVersionId);
        evaluations.importPrecomputed(jd, resume, assessment);
      } catch (RuntimeException failure) {
        // 联合评分是性能优化；无效、过期或竞争结果不得阻断收件，现有任务会安全回退模型评分。
        LOGGER.warn("resume-intake-precomputed-score-fallback jdVersionId={} resumeVersionId={}",
            jdVersionId, resumeVersionId, SafeLogException.from(failure));
      }
      return enqueue(jdVersionId, resumeVersionId);
    });
  }

  @Override
  public ScoringPreparation prepare(String jdVersionId, String requestedByUserId) {
    return actors.execute(requestedByUserId, () -> {
      var jd = matching.requireIntakeScoringReady(jdVersionId);
      var root = json.createObjectNode();
      root.set("professional", FACT_JSON.valueToTree(jd.getProfessional()));
      return new ScoringPreparation(jdVersionId, jd.getSnapshotId(), root.toString(),
          AiScoringConstants.PROMPT_VERSION, AiScoringConstants.SCHEMA_VERSION,
          LocalDate.now(clock));
    });
  }

  private ScoringTaskRef enqueue(String jdVersionId, String resumeVersionId) {
      MatchRun run;
      try {
        run = matching.createForIntake(jdVersionId, resumeVersionId);
      } catch (DuplicateKeyException conflict) {
        // 并发相同输入由数据库唯一键裁决；失败事务退出后重新读取获胜运行。
        run = matching.createForIntake(jdVersionId, resumeVersionId);
      }
      return reference(run);
  }

  @Override
  public ScoringTaskRef get(String runId, String requestedByUserId) {
    return actors.execute(requestedByUserId, () -> reference(matching.get(runId)));
  }

  @Override
  public ScoringTaskRef retry(String runId, String requestedByUserId) {
    return actors.execute(requestedByUserId, () -> {
      matching.retry(runId, "重试导入关联 JD 的评分");
      return reference(matching.get(runId));
    });
  }

  private ScoringTaskRef reference(MatchRun run) {
    var task = tasks.get(run.getTaskId());
    return new ScoringTaskRef(
        task.id(), run.getId(), task.status(), task.errorCode(), task.retryable());
  }
}

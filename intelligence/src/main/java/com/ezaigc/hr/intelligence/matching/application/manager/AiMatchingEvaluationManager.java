package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.enums.AiScoringErrorCode;
import com.ezaigc.hr.intelligence.matching.enums.AiProfessionalAssessmentStatus;
import com.ezaigc.hr.intelligence.matching.enums.MatchingFactType;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiAssessmentMetadataDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.AiProfessionalAssessmentValidator;
import com.ezaigc.hr.intelligence.matching.domain.service.AiScoreWeightingEngine;
import com.ezaigc.hr.intelligence.matching.service.AiProfessionalAssessmentPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 冻结一次 AI 判断，再按用户权重重算；模型网络调用不占数据库事务。 */
@Service
public class AiMatchingEvaluationManager {
  /** 仅用于已筛选的职业事实值对象，支持其 fluent 访问器，不改变 HTTP 的序列化规则。 */
  private static final ObjectMapper FACT_JSON = JsonMapper.builder()
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY)).build();
  @Resource private AiProfessionalAssessmentPersistenceService assessments;
  @Resource private ProfessionalScoringModelPort model;
  @Resource private AiProfessionalAssessmentValidator validator;
  @Resource private AiScoreWeightingEngine weighting;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ObjectMapper json;
  @Resource private TransactionTemplate transactionTemplate;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public MatchEvaluationDTO evaluate(JdFactSnapshotData jd, ResumeFactSnapshotData resume,
      MatchPlanVersion plan, WeightProfileVersion weights, List<ControlledCriterion> criteria,
      Set<String> selected, int planMaxBonus) {
    return weighted(load(jd, resume, true), jd, resume, weights, criteria, selected, planMaxBonus);
  }

  /** 在线预览只能读取既有 AI 分，不调用模型、不持有外部调用事务。 */
  @Transactional(readOnly = true)
  public MatchEvaluationDTO preview(JdFactSnapshotData jd, ResumeFactSnapshotData resume,
      MatchPlanVersion plan, WeightProfileVersion weights, List<ControlledCriterion> criteria,
      Set<String> selected, int planMaxBonus) {
    return weighted(load(jd, resume, false), jd, resume, weights, criteria, selected, planMaxBonus);
  }

  /** 将首次联合解析得到的五维结果写入现有不可变评分缓存，后续匹配任务不再调用模型。 */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public boolean importPrecomputed(JdFactSnapshotData jd, ResumeFactSnapshotData resume,
      PrecomputedAssessment precomputed) {
    if (precomputed == null || jd == null || resume == null
        || !jd.getSnapshotId().equals(precomputed.jdFactSnapshotId())
        || !AiScoringConstants.PROMPT_VERSION.equals(precomputed.promptVersion())
        || !AiScoringConstants.SCHEMA_VERSION.equals(precomputed.schemaVersion())
        || precomputed.evaluatedOn() == null || precomputed.provider() == null
        || precomputed.provider().isBlank() || precomputed.modelVersion() == null
        || precomputed.modelVersion().isBlank() || precomputed.resultJson() == null) {
      return false;
    }
    String key = cacheKey(jd, resume);
    if (succeeded(assessments.findByCacheKey(key))) return true;
    var validated = validator.validatePrecomputed(precomputed.resultJson(), jd, resume);
    String normalized = json.writeValueAsString(validated);
    Instant now = clock.instant();
    var claim = new AiProfessionalAssessment();
    claim.setId(ids.newId());
    claim.setCacheKey(key);
    claim.setJdFactSnapshotId(jd.getSnapshotId());
    claim.setResumeFactSnapshotId(resume.getSnapshotId());
    claim.setPromptVersion(precomputed.promptVersion());
    claim.setSchemaVersion(precomputed.schemaVersion());
    claim.setProvider(precomputed.provider());
    claim.setModel(precomputed.modelVersion());
    claim.setEvaluatedOn(precomputed.evaluatedOn());
    claim.setStatus(AiProfessionalAssessmentStatus.PROCESSING.getCode());
    claim.setClaimToken(ids.newId());
    claim.setLeaseUntil(now.plusSeconds(AiScoringConstants.CLAIM_LEASE_SECONDS));
    boolean acquired = Boolean.TRUE.equals(
        transactionTemplate.execute(ignored -> assessments.claim(claim, now)));
    if (!acquired) return succeeded(assessments.findByCacheKey(key));
    boolean completed = Boolean.TRUE.equals(transactionTemplate.execute(ignored -> assessments.complete(
        claim.getId(), claim.getClaimToken(), normalized, precomputed.provider(),
        precomputed.modelVersion(), precomputed.inputTokens(), precomputed.outputTokens(),
        precomputed.durationMillis(), clock.instant())));
    return completed && succeeded(assessments.findByCacheKey(key));
  }

  private MatchEvaluationDTO weighted(AiProfessionalAssessment stored, JdFactSnapshotData jd,
      ResumeFactSnapshotData resume, WeightProfileVersion weights, List<ControlledCriterion> criteria,
      Set<String> selected, int planMaxBonus) {
    var assessment = validator.validate(stored.getResultJson(), jd, resume);
    var metadata = new AiAssessmentMetadataDTO(stored.getId(), stored.getProvider(), stored.getModel(),
        stored.getPromptVersion(), stored.getSchemaVersion(), stored.getEvaluatedOn(),
        assessment.getProfessionalScore(), assessment.getSummary());
    return weighting.calculate(jd, resume, weights, criteria, selected, planMaxBonus, assessment, metadata);
  }

  private AiProfessionalAssessment load(JdFactSnapshotData jd, ResumeFactSnapshotData resume, boolean allowModel) {
    String key = cacheKey(jd, resume);
    var existing = assessments.findByCacheKey(key);
    if (succeeded(existing)) return existing;
    if (!allowModel) {
      throw PlatformExceptions.conflict(AiScoringErrorCode.MATCH_AI_ASSESSMENT_REQUIRED.getCode(), "尚无本版本的 AI 评分，请先发起评分任务，再调整权重预览");
    }
    String jdInput = jdInput(jd);
    String resumeInput = resumeInput(resume);
    if (jdInput.length() + resumeInput.length() > AiScoringConstants.MAX_INPUT_CHARACTERS) {
      throw PlatformExceptions.conflict(AiScoringErrorCode.MATCH_AI_INPUT_TOO_LARGE.getCode(), "评分事实超出模型输入上限，请复核简历和岗位内容");
    }
    var identity = model.cacheIdentity();
    if (identity == null || identity.provider() == null || identity.modelVersion() == null) {
      throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, AiScoringErrorCode.MATCH_AI_MODEL_UNAVAILABLE.getCode(),
          "AI 评分模型尚未启用，请检查模型配置后重试", true);
    }
    var now = clock.instant();
    var claim = new AiProfessionalAssessment();
    claim.setId(ids.newId());
    claim.setCacheKey(key);
    claim.setJdFactSnapshotId(jd.getSnapshotId());
    claim.setResumeFactSnapshotId(resume.getSnapshotId());
    claim.setPromptVersion(AiScoringConstants.PROMPT_VERSION);
    claim.setSchemaVersion(AiScoringConstants.SCHEMA_VERSION);
    claim.setProvider(identity.provider());
    claim.setModel(identity.modelVersion());
    claim.setEvaluatedOn(LocalDate.now(clock));
    claim.setStatus(AiProfessionalAssessmentStatus.PROCESSING.getCode());
    claim.setClaimToken(ids.newId());
    claim.setLeaseUntil(now.plusSeconds(AiScoringConstants.CLAIM_LEASE_SECONDS));
    boolean acquired = Boolean.TRUE.equals(transactionTemplate.execute(ignored -> assessments.claim(claim, now)));
    var stored = assessments.findByCacheKey(key);
    if (succeeded(stored)) return stored;
    if (!acquired || stored == null || !claim.getClaimToken().equals(stored.getClaimToken())) {
      throw new PlatformException(HttpStatus.CONFLICT, AiScoringErrorCode.MATCH_AI_ASSESSMENT_BUSY.getCode(), "同一简历和岗位的 AI 评分正在处理中，请稍后重试", true);
    }
    try {
      var result = model.score(new ProfessionalScoringModelPort.ScoreRequest(jdInput, resumeInput,
          AiScoringConstants.PROMPT_VERSION, AiScoringConstants.SCHEMA_VERSION, stored.getEvaluatedOn()));
      var validated = validator.validate(result.rawJson(), jd, resume);
      String normalized = json.writeValueAsString(validated);
      boolean completed = Boolean.TRUE.equals(transactionTemplate.execute(ignored -> assessments.complete(
          stored.getId(), claim.getClaimToken(), normalized, result.provider(), result.modelVersion(),
          result.inputTokens(), result.outputTokens(), result.durationMillis(), clock.instant())));
      if (!completed) {
        throw new PlatformException(HttpStatus.CONFLICT, AiScoringErrorCode.MATCH_AI_ASSESSMENT_LEASE_LOST.getCode(), "AI 评分保存租约已失效，请重试任务", true);
      }
      var completedAssessment = assessments.findByCacheKey(key);
      if (!succeeded(completedAssessment)) throw new IllegalStateException("AI assessment completion was not persisted");
      return completedAssessment;
    } catch (RuntimeException failure) {
      if (failure instanceof PlatformException problem
          && AiScoringErrorCode.MATCH_AI_ASSESSMENT_LEASE_LOST.matches(problem.code())) throw problem;
      transactionTemplate.executeWithoutResult(ignored -> assessments.fail(stored.getId(), claim.getClaimToken(),
          AiScoringErrorCode.MATCH_AI_SCORING_FAILED.getCode(), clock.instant()));
      // 不将供应商原始文本、简历内容或请求体透传到日志/接口。
      throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, AiScoringErrorCode.MATCH_AI_SCORING_FAILED.getCode(),
          "AI 评分未完成，请稍后重试或检查模型配置；未使用规则评分替代", true);
    }
  }

  private static boolean succeeded(AiProfessionalAssessment assessment) {
    return assessment != null && AiProfessionalAssessmentStatus.SUCCEEDED.matches(assessment.getStatus());
  }

  static String cacheKey(JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
    // 日期、权重、操作者和供应商配置变化均不得悄悄覆盖同一份已经冻结的模型判断。
    return Sha256Utils.hashUtf8(jd.getSnapshotId() + "|" + resume.getSnapshotId() + "|"
        + AiScoringConstants.PROMPT_VERSION + "|" + AiScoringConstants.SCHEMA_VERSION);
  }

  private String jdInput(JdFactSnapshotData jd) {
    ObjectNode input = json.createObjectNode();
    input.set("professional", FACT_JSON.valueToTree(jd.getProfessional()));
    return input.toString();
  }

  private String resumeInput(ResumeFactSnapshotData resume) {
    ObjectNode input = json.createObjectNode();
    ObjectNode candidate = input.putObject("candidate");
    if (resume.getCandidate() != null) {
      candidate.put("currentRole", resume.getCandidate().currentRole());
      candidate.put("jobIntention", resume.getCandidate().jobIntention());
    }
    input.set("skills", FACT_JSON.valueToTree(resume.getSkills()));
    input.set("workExperiences", FACT_JSON.valueToTree(resume.getWorkExperiences()));
    input.set("projectExperiences", FACT_JSON.valueToTree(resume.getProjectExperiences()));
    input.set("responsibilityTags", FACT_JSON.valueToTree(resume.getSimpleFacts().stream()
        .filter(fact -> MatchingFactType.RESPONSIBILITY_TAG.matches(fact.type())).toList()));
    input.set("industryTags", FACT_JSON.valueToTree(resume.getSimpleFacts().stream()
        .filter(fact -> MatchingFactType.INDUSTRY_TAG.matches(fact.type())).toList()));
    return input.toString();
  }
}

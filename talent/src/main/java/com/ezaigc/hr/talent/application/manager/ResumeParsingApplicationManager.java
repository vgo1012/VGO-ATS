package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_FACT_CORRECTION_CHANGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EXTRACTION_PAGES;

import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_PROMPT_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_TASK_TYPE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort;
import com.ezaigc.hr.common.infrastructure.task.TaskRetryBackoff;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskErrorCode;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import org.springframework.context.annotation.Lazy;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.application.assembler.ResumeParsingAssembler;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.api.enums.ResumeFactErrorCode;
import com.ezaigc.hr.talent.enums.ResumeFactSnapshotSource;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Credential;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Education;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Language;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelUnavailableException;
import com.ezaigc.hr.common.infrastructure.ai.AiQuotaExceededException;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ParseRequest;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ProjectExperience;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Skill;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.WorkExperience;
import com.ezaigc.hr.talent.domain.dto.CorrectResumeFactsDTO;
import com.ezaigc.hr.talent.domain.dto.CorrectionMutationDTO;
import com.ezaigc.hr.talent.domain.dto.CorrectionPersistenceResultDTO;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.domain.entity.FactBundle;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.domain.response.FactCorrectionResponse;
import com.ezaigc.hr.talent.enums.ResumeExtractionErrorCode;
import com.ezaigc.hr.talent.enums.ResumeFactType;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParseSnapshotPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator.ContractViolation;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import com.ezaigc.hr.talent.domain.service.FactDateMetadata;
import com.ezaigc.hr.talent.domain.service.CandidateBirthMetadata;
import java.util.Objects;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;

/** 简历解析应用服务，负责用例编排、权限校验和事务边界。 */
@Service
public class ResumeParsingApplicationManager {
  public static final String INTAKE_ADOPTION_TASK_TYPE = "RESUME_INTAKE_ADOPT";
  private static final Logger LOGGER = LoggerFactory.getLogger(ResumeParsingApplicationManager.class);

  @Resource private ResumeParsingAssembler assembler;

  @Resource private Clock clock;

  @Resource private PlatformIdGenerator ids;
  @Resource private ObjectMapper json;

  @Resource private ResumeParsingPersistenceService persistence;
  @Resource private CandidatePersistenceService candidates;
  @Resource private ResumeVersionPersistenceService resumeVersions;
  @Resource private EducationFactPersistenceService educationFactPersistenceService;
  @Resource private ExtractionRunPersistenceService extractionRunPersistenceService;
  @Resource private ProjectFactPersistenceService projectFactPersistenceService;
  @Resource private ResumeExtractionPagePersistenceService resumeExtractionPagePersistenceService;
  @Resource private ResumeModelRunPersistenceService resumeModelRunPersistenceService;
  @Resource private ResumeParseSnapshotPersistenceService resumeParseSnapshotPersistenceService;
  @Resource private SimpleFactPersistenceService simpleFactPersistenceService;
  @Resource private SkillFactPersistenceService skillFactPersistenceService;
  @Resource private WorkFactPersistenceService workFactPersistenceService;
  @Resource private AsyncTaskPort tasks;
  @Resource private AuthorizationService authorization;
  @Resource @Lazy private CandidateLibraryQueryService library;
  @Resource private ResumeDocumentExtractionPort extraction;
  @Resource private ResumeParseModelPort model;
  @Resource private RawAiArchivePort archive;
  @Resource private ResumeParseContractValidator validator;
  @Resource private ResumeFactPersistenceManager factPersistence;
  @Resource private ObjectStoragePort storage;
  @Resource private TransactionTemplate transactionTemplate;

  public TaskSnapshot enqueue(String versionId, String idempotencyKey) {
    ResumeResourceEntity resource = requireResource(versionId);
    authorization.requireAccess("talent.manage", target(resource));
    FactSnapshot current = persistence.findCurrentFactSnapshot(versionId);
    if (current != null && ResumeFactSnapshotSource.HUMAN_CORRECTION.matches(current.getSourceType())) {
      throw PlatformExceptions.conflict(
          ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getCode(), ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getDesc());
    }
    var actor = authorization.currentActor();
    Instant now = clock.instant();
    return tasks.enqueue(
        new EnqueueTask(
            ids.newId(),
            RESUME_PARSE_TASK_TYPE,
            "RESUME_VERSION",
            versionId,
            actor.getUserId(),
            resource.getOwnerUserId(),
            resource.getOwnerDepartmentId(),
            "talent.read",
            "talent.manage",
            idempotencyKey,
            fingerprint(
                resource.getContentSha256(),
                RESUME_FACT_SCHEMA_VERSION,
                RESUME_PARSE_PROMPT_VERSION),
            3,
            now));
  }

  /** 仅排队归档已解析事实；不会再次调用文档抽取器或模型。 */
  @Transactional
  public TaskSnapshot adoptIntakeResult(String versionId, ResumeIntakeProcessing result) {
    ResumeResourceEntity resource = requireResource(versionId);
    authorization.requireAccess("talent.manage", target(resource));
    requireIntakeResult(resource, result);
    // 共用简历版本锁，避免不同操作人同时为同一共享结果创建归档任务。
    lockResumeVersionForRouting(versionId);
    TaskSnapshot existing = intakeAdoptionTask(versionId, result.getId());
    if (existing != null) return existing;
    var actor = authorization.currentActor();
    return tasks.enqueue(new EnqueueTask(ids.newId(), INTAKE_ADOPTION_TASK_TYPE, "RESUME_VERSION",
        versionId, actor.getUserId(), resource.getOwnerUserId(), resource.getOwnerDepartmentId(),
        "talent.read", "talent.manage", "intake-adopt:" + versionId + ":" + result.getId(),
        fingerprint(versionId, result.getId(), result.getContentSha256()), 3, clock.instant()));
  }

  /** 人工重试只恢复原归档任务，保留其事实来源与审计。 */
  @Transactional
  public TaskSnapshot retryIntakeAdoption(String versionId, String processingId, String reason) {
    ResumeResourceEntity resource = requireResource(versionId);
    authorization.requireAccess("talent.manage", target(resource));
    TaskSnapshot task = intakeAdoptionTask(versionId, processingId);
    if (task == null || !INTAKE_ADOPTION_TASK_TYPE.equals(task.taskType())
        || !versionId.equals(task.resourceId())) {
      throw PlatformExceptions.conflict("INTAKE_ADOPTION_TASK_MISSING", "原归档任务不存在，请重新检查收件状态");
    }
    if (AsyncTaskStatus.FAILED.matches(task.status())) {
      if (!task.retryable()) {
        throw PlatformExceptions.conflict("INTAKE_ADOPTION_NOT_RETRYABLE", "原归档任务不可重试，请核对失败原因");
      }
      return tasks.retryFailed(task.id(), reason);
    }
    if (AsyncTaskStatus.CANCELLED.matches(task.status()) || AsyncTaskStatus.CANCELLING.matches(task.status())) {
      throw PlatformExceptions.conflict("INTAKE_ADOPTION_CANCELLED", "原归档任务已取消，请重新处理收件");
    }
    return task;
  }

  private TaskSnapshot intakeAdoptionTask(String versionId, String processingId) {
    return tasks.getByResourceIdempotency(INTAKE_ADOPTION_TASK_TYPE, "RESUME_VERSION", versionId,
        "intake-adopt:" + versionId + ":" + processingId);
  }

  private void lockResumeVersionForRouting(String versionId) {
    if (resumeVersions.lockForRouting(versionId) == null) {
      throw PlatformExceptions.conflict("INTAKE_ADOPTION_RESOURCE_MISSING", "归档简历版本已不可用");
    }
  }

  /** 将共享结果原子归档为具体简历版本的事实，复制过程中没有外部调用。 */
  @Transactional
  public void processIntakeAdoption(TaskSnapshot task, ResumeIntakeProcessing result) {
    // 与入队统一先锁版本、再锁任务，避免抽取记录外键检查产生反向等待。
    lockResumeVersionForRouting(task.resourceId());
    requireOwnedLease(task);
    ResumeResourceEntity resource = requireResource(task.resourceId());
    authorization.requireAccess("talent.manage", target(resource));
    requireIntakeResult(resource, result);
    FactSnapshot existing = persistence.findCurrentFactSnapshot(resource.getResumeVersionId());
    if (existing != null) {
      tasks.succeed(task, existing.getId());
      return;
    }
    var source = json.readValue(result.getExtractedPagesJson(), IntakeParsedSourceDTO.class);
    var document = new ExtractedDocument(source.getExtractorVersion(), source.getRoute(), source.getPageCount(),
        source.getSuccessfulPages(), source.getFallbackPages(), source.getPrintableRatio(), source.getCoverageRatio(),
        source.getPages().stream().map(page -> new ExtractedPage(page.getPageNo(), page.getRoute(), page.getText(),
            page.getCharacterCount(), page.getPrintableRatio(), page.getQualityScore(), page.getContentHash())).toList());
    validateExtractionResult(document);
    List<PageText> pages = document.pages().stream().map(page -> new PageText(page.pageNo(), page.text())).toList();
    ResumeFacts facts = validator.validate(result.getParsedFactsJson(), pages);
    String extractionId = ids.newId();
    requireSingleWrite(extractionRunPersistenceService.insertExtractionRun(
        extractionId, resource.getResumeVersionId(), task.id(), clock.instant()), "归档共享抽取运行", extractionId);
    persistExtraction(extractionId, document);
    String modelRunId = ids.newId();
    requireSingleWrite(resumeModelRunPersistenceService.insertModelRun(modelRunId, resource.getResumeVersionId(),
        extractionId, source.getSchemaVersion(), source.getPromptVersion(), clock.instant()), "归档共享模型运行", modelRunId);
    var modelResult = new ModelResult(result.getModelProvider(), result.getModelVersion(), null,
        source.getInputTokens(), source.getOutputTokens(), source.getDurationMillis());
    var archived = new ArchiveResult(result.getRawOutputObjectKey(), result.getRawOutputHash(),
        result.getRawOutputSize(), result.getEncryptionVersion());
    markModelRunComplete(modelRunId, modelResult, archived);
    FactSnapshot snapshot = factPersistence.storeAiFacts(resource.getResumeVersionId(), extractionId, modelRunId,
        source.getSchemaVersion(), source.getPromptVersion(), fingerprint("intake-adopt", resource.getResumeVersionId(),
            result.getId(), source.getSchemaVersion(), source.getPromptVersion()), facts, source.getAgeAsOf());
    requireSnapshot(snapshot);
    tasks.succeed(task, snapshot.getId());
  }

  private static void requireIntakeResult(ResumeResourceEntity resource, ResumeIntakeProcessing result) {
    if (result == null || !ResumeIntakeProcessingStatus.SUCCEEDED.matches(result.getStatus())
        || !resource.getContentSha256().equals(result.getContentSha256())
        || result.getParsedFactsJson() == null || result.getExtractedPagesJson() == null) {
      throw PlatformExceptions.conflict("INTAKE_RESULT_NOT_READY", "共享解析结果尚未就绪或与简历内容不一致");
    }
  }

  @Transactional(readOnly = true)
  public FactBundle facts(String versionId) {
    requireReadableResource(versionId);
    FactSnapshot snapshot = persistence.findCurrentFactSnapshot(versionId);
    if (snapshot == null)
      throw new PlatformException(HttpStatus.NOT_FOUND, "RESUME_FACTS_NOT_READY", "简历事实尚未生成", true);
    return bundle(snapshot);
  }

  public FactBundleResponse factResponse(String versionId) {
    return assembler.toResponse(facts(versionId));
  }

  @Transactional(readOnly = true)
  public FactBundle historicalFacts(String versionId, String snapshotId) {
    requireReadableResource(versionId);
    FactSnapshot snapshot = persistence.findFactSnapshot(snapshotId);
    if (snapshot == null || !versionId.equals(snapshot.getResumeVersionId())) {
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "RESUME_FACT_SNAPSHOT_NOT_FOUND", "该简历版本的事实快照不存在", false);
    }
    return bundle(snapshot);
  }

  public FactBundleResponse historicalFactResponse(String versionId, String snapshotId) {
    return assembler.toResponse(historicalFacts(versionId, snapshotId));
  }

  @Transactional
  public FactCorrectionResponse correct(CorrectResumeFactsDTO command) {
    if (command == null
        || CommonUtils.isBlank(command.getResumeVersionId())
        || CommonUtils.isBlank(command.getBaseFactSnapshotId())
        || CommonUtils.isBlank(command.getReason())
        || command.getReason().length() > 500
        || command.getFacts() == null)
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, "FACT_CORRECTION_INVALID", "纠正请求缺少必要字段或超出长度限制", true);
    String source = "HUMAN_PROVIDED";
    ResumeResourceEntity preliminary = requireResource(command.getResumeVersionId());
    // 与共享领取/路由一致，先锁候选人再锁版本；预查只用于定位，不用于最终授权。
    if (candidates.lockCandidate(preliminary.getCandidateId()) == null) {
      throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "简历归属已变化，请刷新后重试");
    }
    lockResumeVersionForRouting(command.getResumeVersionId());
    ResumeResourceEntity currentResource = persistence.findResumeResourceForUpdate(
        command.getResumeVersionId(), preliminary.getCandidateId());
    if (currentResource == null) {
      throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "简历归属已变化或版本不可用，请刷新后重试");
    }
    // 更正沿用原对象授权；共享只开放读取，不扩大人工修改范围。
    authorization.requireAccess("talent.read", target(currentResource));
    var actor = authorization.currentActor();
    FactSnapshot current = persistence.findCurrentFactSnapshotForUpdate(command.getResumeVersionId());
    if (current == null)
      throw new PlatformException(HttpStatus.CONFLICT, "RESUME_FACTS_NOT_READY", "简历事实尚未生成", true);
    if (!command.getBaseFactSnapshotId().equals(current.getId())) {
      throw PlatformExceptions.conflict("FACT_SNAPSHOT_CHANGED", "事实快照已变化，请刷新后重新提交纠正");
    }
    FactBundle oldBundle = bundle(current);
    ResumeFacts oldFacts = toFacts(oldBundle);
    ResumeFacts proposed = command.getFacts();
    ResumeFacts normalized =
        new ResumeFacts(
            preserveBirthDate(proposed.candidate(), oldFacts.candidate()),
            proposed.workExperiences(),
            proposed.projectExperiences(),
            proposed.educations(),
            proposed.skills(),
            proposed.credentials(),
            proposed.languages(),
            proposed.industryTags(),
            proposed.responsibilityTags(),
            proposed.unknownItems());
    try {
      validator.validateHumanFacts(normalized);
    } catch (ContractViolation ex) {
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, ex.errorCode(), "纠正后的事实不符合结构约束", true);
    }
    List<CorrectionMutationDTO> changes =
        diff(json.valueToTree(oldFacts), json.valueToTree(normalized), "");
    if (changes.isEmpty())
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT, "FACT_CORRECTION_NO_CHANGES", "纠正内容没有发生变化", true);
    if (changes.size() > MAX_FACT_CORRECTION_CHANGES)
      throw new PlatformException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "FACT_CORRECTION_TOO_LARGE",
          "单次纠正最多修改 " + MAX_FACT_CORRECTION_CHANGES + " 个事实字段",
          true);
    List<String> capabilities = affectedCapabilities(changes);
    CorrectionPersistenceResultDTO result =
        factPersistence.storeCorrection(
            command.getResumeVersionId(),
            command.getBaseFactSnapshotId(),
            actor.getUserId(),
            command.getReason().strip(),
            source,
            capabilities,
            changes,
            normalized);
    return new FactCorrectionResponse(
        result.getCorrectionId(),
        result.getFactSnapshotId(),
        result.getSnapshotVersion(),
        result.getAffectedCapabilities());
  }

  /**
   * 处理当前业务操作。
   * Invoked only by the durable worker. Raw document/model payloads are deliberately never logged.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void process(TaskSnapshot task) {
    if (tasks.shouldStopProcessing(task)) {
      tasks.cancelled(task);
      return;
    }
    ResumeResourceEntity resource = persistence.findResumeResource(task.resourceId());
    if (resource == null) {
      tasks.fail(task, "RESUME_VERSION_NOT_FOUND", "简历版本不存在或不可用", false);
      return;
    }
    String extractionId = ids.newId();
    startExtractionRun(extractionId, resource.getResumeVersionId(), task);
    ExtractedDocument document = extractAndPersist(task, resource, extractionId);
    if (document == null) return;
    if (tasks.shouldStopProcessing(task)) {
      tasks.cancelled(task);
      return;
    }

    ModelIdentity modelIdentity = model.cacheIdentity();
    String cacheKey = modelIdentity == null
            || CommonUtils.isBlank(modelIdentity.provider())
            || CommonUtils.isBlank(modelIdentity.modelVersion())
        ? null
        : parseCacheKey(resource, document, modelIdentity.provider(), modelIdentity.modelVersion());
    if (cacheKey != null && completeFromCurrentCache(task, resource, cacheKey)) return;

    String modelRunId = ids.newId();
    startModelRun(modelRunId, resource.getResumeVersionId(), extractionId, task);
    List<PageText> pages =
        document.pages().stream().map(v -> new PageText(v.pageNo(), v.text())).toList();
    ModelResult result = invokeModel(task, resource, modelRunId, pages);
    if (result == null) return;
    ArchiveResult archived = archiveModelResult(task, resource, modelRunId, result);
    if (archived == null) return;
    ResumeFacts facts = validateModelResult(task, modelRunId, result, archived, pages);
    if (facts == null) return;
    if (tasks.shouldStopProcessing(task)) {
      cancelModelRunAndTask(task, modelRunId, result, archived);
      return;
    }
    completeParsing(task, resource, extractionId, document, modelRunId, result, archived, facts,
        cacheKey);
  }

  private ExtractedDocument extractAndPersist(
      TaskSnapshot task, ResumeResourceEntity resource, String extractionId) {
    ExtractedDocument document;
    try (InputStream content = storage.open(resource.getObjectKey())) {
      document = extraction.extract(resource.getOriginalFileName(), content);
      if (document == null) {
        throw new IllegalStateException("document extraction returned no result");
      }
    } catch (IOException ex) {
      LOGGER.error(
          "resume-extraction-failed taskId={} resumeVersionId={} extractionId={}",
          task.id(),
          resource.getResumeVersionId(),
          extractionId,
          SafeLogException.from(ex));
      String errorCode = extractionCode(ex);
      failExtractionAndTask(
          task,
          extractionId,
          errorCode,
          extractionFailureDetail(errorCode),
          retryableExtraction(ex));
      return null;
    } catch (RuntimeException ex) {
      LOGGER.error(
          "resume-extraction-failed taskId={} resumeVersionId={} extractionId={}",
          task.id(),
          resource.getResumeVersionId(),
          extractionId,
          SafeLogException.from(ex));
      failExtractionAndTask(
          task,
          extractionId,
          "DOCUMENT_EXTRACTION_FAILED",
          "简历正文提取失败，请检查文件质量或稍后重试",
          true);
      return null;
    }

    try {
      persistExtraction(extractionId, document);
      return document;
    } catch (RuntimeException ex) {
      LOGGER.error(
          "resume-extraction-persistence-failed taskId={} resumeVersionId={} extractionId={}",
          task.id(),
          resource.getResumeVersionId(),
          extractionId,
          SafeLogException.from(ex));
      failExtractionAndTask(
          task,
          extractionId,
          "EXTRACTION_PERSISTENCE_FAILED",
          "简历正文持久化失败，请稍后重试",
          true);
      return null;
    }
  }

  private ModelResult invokeModel(
      TaskSnapshot task,
      ResumeResourceEntity resource,
      String modelRunId,
      List<PageText> pages) {
    ModelResult result;
    try {
      result =
          model.parse(new ParseRequest(RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION,
              resource.getOriginalFileName(), pages));
    } catch (ModelUnavailableException | AiQuotaExceededException ex) {
      boolean quotaExceeded = ex instanceof AiQuotaExceededException;
      LOGGER.warn(
          "resume-model-unavailable taskId={} resumeVersionId={} modelRunId={}",
          task.id(),
          resource.getResumeVersionId(),
          modelRunId,
          SafeLogException.from(ex));
      failModelRunAndTask(
          task,
          modelRunId,
          AiOutputValidationStatus.MODEL_UNAVAILABLE,
          quotaExceeded ? AiQuotaExceededException.CODE : "MODEL_UNAVAILABLE",
          null,
          null,
          quotaExceeded ? AiQuotaExceededException.USER_MESSAGE : "简历解析模型尚未启用或暂时不可用");
      return null;
    } catch (RuntimeException ex) {
      LOGGER.error(
          "resume-model-call-failed taskId={} resumeVersionId={} modelRunId={}",
          task.id(),
          resource.getResumeVersionId(),
          modelRunId,
          SafeLogException.from(ex));
      failModelRunAndTask(
          task,
          modelRunId,
          AiOutputValidationStatus.MODEL_UNAVAILABLE,
          "MODEL_CALL_FAILED",
          null,
          null,
          "简历解析模型调用失败，请稍后重试");
      return null;
    }
    if (result == null
        || CommonUtils.isBlank(result.provider())
        || CommonUtils.isBlank(result.modelVersion())
        || CommonUtils.isBlank(result.rawJson())) {
      failModelRunAndTask(
          task,
          modelRunId,
          AiOutputValidationStatus.SCHEMA_INVALID,
          "MODEL_RESULT_EMPTY",
          null,
          null,
          "模型未返回有效结果");
      return null;
    }
    return result;
  }

  private ArchiveResult archiveModelResult(
      TaskSnapshot task,
      ResumeResourceEntity resource,
      String modelRunId,
      ModelResult result) {
    try {
      return archive.archive(
          "ai-raw/resume/" + resource.getResumeVersionId() + "/" + modelRunId + ".json.gz.enc",
          result.rawJson());
    } catch (IOException | RuntimeException ex) {
      LOGGER.error(
          "resume-model-archive-failed taskId={} resumeVersionId={} modelRunId={}",
          task.id(),
          resource.getResumeVersionId(),
          modelRunId,
          SafeLogException.from(ex));
      failModelRunAndTask(
          task,
          modelRunId,
          AiOutputValidationStatus.PENDING,
          "AI_RAW_ARCHIVE_FAILED",
          result,
          null,
          "模型原始输出归档失败，请稍后重试");
      return null;
    }
  }

  private ResumeFacts validateModelResult(
      TaskSnapshot task,
      String modelRunId,
      ModelResult result,
      ArchiveResult archived,
      List<PageText> pages) {
    try {
      return validator.validateModel(result.rawJson(), pages);
    } catch (ContractViolation ex) {
      failModelRunAndTask(
          task,
          modelRunId,
          ex.validationStatus(),
          ex.errorCode(),
          result,
          archived,
          ex.safeDetail());
      return null;
    } catch (RuntimeException ex) {
      LOGGER.error(
          "resume-model-validation-failed taskId={} modelRunId={}", task.id(), modelRunId, SafeLogException.from(ex));
      failModelRunAndTask(
          task,
          modelRunId,
          AiOutputValidationStatus.SCHEMA_INVALID,
          "MODEL_OUTPUT_VALIDATION_FAILED",
          result,
          archived,
          "模型输出校验失败，请稍后重试");
      return null;
    }
  }

  private void completeParsing(
      TaskSnapshot task,
      ResumeResourceEntity resource,
      String extractionId,
      ExtractedDocument document,
      String modelRunId,
      ModelResult result,
      ArchiveResult archived,
      ResumeFacts facts,
      String plannedCacheKey) {
    String cacheKey = plannedCacheKey == null
        ? parseCacheKey(resource, document, result.provider(), result.modelVersion())
        : plannedCacheKey;
    try {
      transactionTemplate.executeWithoutResult(
          ignored -> {
            markModelRunComplete(modelRunId, result, archived);
            FactSnapshot snapshot =
                factPersistence.storeAiFacts(
                    resource.getResumeVersionId(),
                    extractionId,
                    modelRunId,
                    RESUME_FACT_SCHEMA_VERSION,
                    RESUME_PARSE_PROMPT_VERSION,
                    cacheKey,
                    facts);
            requireSnapshot(snapshot);
            tasks.succeed(task, snapshot.getId());
          });
    } catch (DuplicateKeyException ex) {
      String parseId = resumeParseSnapshotPersistenceService.findParseSnapshotByCache(cacheKey);
      FactSnapshot cached = parseId == null ? null : persistence.findFactSnapshotByParse(parseId);
      if (cached == null || CommonUtils.isBlank(cached.getId())) {
        LOGGER.warn(
            "resume-fact-persistence-conflict taskId={} resumeVersionId={} modelRunId={}",
            task.id(),
            resource.getResumeVersionId(),
            modelRunId,
            SafeLogException.from(ex));
        failModelRunAndTask(
            task,
            modelRunId,
            AiOutputValidationStatus.VALID,
            "FACT_PERSISTENCE_CONFLICT",
            result,
            archived,
            "事实快照持久化冲突，请重试");
        return;
      }
      completeFromCachedFacts(task, resource, modelRunId, result, archived, cached);
    } catch (RuntimeException ex) {
      handleFactPersistenceFailure(task, resource, modelRunId, result, archived, ex);
    }
  }

  /** 仅复用当前简历版本仍在生效的同模型结果；不会跨版本共享事实快照。 */
  private boolean completeFromCurrentCache(
      TaskSnapshot task, ResumeResourceEntity resource, String cacheKey) {
    if (resumeParseSnapshotPersistenceService.findParseSnapshotByCache(cacheKey) == null) {
      return false;
    }
    Boolean completed = transactionTemplate.execute(
        ignored -> {
          lockResumeVersionForRouting(resource.getResumeVersionId());
          requireOwnedLease(task);
          FactSnapshot current =
              persistence.findCurrentFactSnapshotForUpdate(resource.getResumeVersionId());
          if (current != null
              && ResumeFactSnapshotSource.HUMAN_CORRECTION.matches(current.getSourceType())) {
            tasks.fail(
                task,
                ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getCode(),
                ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getDesc(),
                false);
            return true;
          }
          String parseId = resumeParseSnapshotPersistenceService.findParseSnapshotByCache(cacheKey);
          FactSnapshot cached = parseId == null ? null : persistence.findFactSnapshotByParse(parseId);
          if (cached == null
              || current == null
              || !resource.getResumeVersionId().equals(cached.getResumeVersionId())
              || !current.getId().equals(cached.getId())
              || !ResumeFactSnapshotSource.AI_PARSE.matches(cached.getSourceType())) {
            return false;
          }
          tasks.succeed(task, cached.getId());
          return true;
        });
    return Boolean.TRUE.equals(completed);
  }

  private static String parseCacheKey(
      ResumeResourceEntity resource,
      ExtractedDocument document,
      String provider,
      String modelVersion) {
    return fingerprint(
        "resume-parse-cache-v2",
        resource.getResumeVersionId(),
        resource.getContentSha256(),
        document.extractorVersion(),
        RESUME_FACT_SCHEMA_VERSION,
        RESUME_PARSE_PROMPT_VERSION,
        provider,
        modelVersion);
  }

  private void completeFromCachedFacts(
      TaskSnapshot task,
      ResumeResourceEntity resource,
      String modelRunId,
      ModelResult result,
      ArchiveResult archived,
      FactSnapshot cached) {
    try {
      transactionTemplate.executeWithoutResult(
          ignored -> {
            lockResumeVersionForRouting(resource.getResumeVersionId());
            requireOwnedLease(task);
            FactSnapshot current =
                persistence.findCurrentFactSnapshotForUpdate(resource.getResumeVersionId());
            if (!resource.getResumeVersionId().equals(cached.getResumeVersionId())
                || current == null
                || !cached.getId().equals(current.getId())
                || !ResumeFactSnapshotSource.AI_PARSE.matches(cached.getSourceType())) {
              throw PlatformExceptions.conflict(
                  ResumeFactErrorCode.FACT_CACHE_LINEAGE_CONFLICT.getCode(),
                  ResumeFactErrorCode.FACT_CACHE_LINEAGE_CONFLICT.getDesc());
            }
            markModelRunComplete(modelRunId, result, archived);
            tasks.succeed(task, cached.getId());
          });
    } catch (RuntimeException ex) {
      handleFactPersistenceFailure(task, resource, modelRunId, result, archived, ex);
    }
  }

  private void handleFactPersistenceFailure(
      TaskSnapshot task,
      ResumeResourceEntity resource,
      String modelRunId,
      ModelResult result,
      ArchiveResult archived,
      RuntimeException ex) {
    LOGGER.error(
        "resume-fact-persistence-failed taskId={} resumeVersionId={} modelRunId={}",
        task.id(),
        resource.getResumeVersionId(),
        modelRunId,
        SafeLogException.from(ex));
    String errorCode = ex instanceof PlatformException platform
        ? platform.code() : "FACT_PERSISTENCE_FAILED";
    String safeDetail = ex instanceof PlatformException platform
        ? platform.getMessage() : "事实快照持久化失败，请重试";
    failModelRunAndTask(
        task,
        modelRunId,
        AiOutputValidationStatus.VALID,
        errorCode,
        result,
        archived,
        safeDetail,
        !ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.matches(errorCode)
            && !ResumeFactErrorCode.FACT_CACHE_LINEAGE_CONFLICT.matches(errorCode));
  }

  private void startExtractionRun(String extractionId, String versionId, TaskSnapshot task) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          requireOwnedLease(task);
          Instant now = clock.instant();
          if (task.attemptCount() > 1) {
            resumeModelRunPersistenceService.terminateWithoutWorker(task.id(), versionId, "TASK_LEASE_EXPIRED",
                now, PlatformActorConstants.SYSTEM_ACTOR_ID);
            extractionRunPersistenceService.terminateWithoutWorker(task.id(), versionId, "TASK_LEASE_EXPIRED",
                now, PlatformActorConstants.SYSTEM_ACTOR_ID);
          }
          requireSingleWrite(
              extractionRunPersistenceService.insertExtractionRun(extractionId, versionId, task.id(), now),
              "创建简历抽取运行",
              extractionId);
        });
  }

  private void persistExtraction(String extractionId, ExtractedDocument document) {
    List<ResumeExtractionPage> pages = createExtractionPages(extractionId, document);
    transactionTemplate.executeWithoutResult(
        ignored -> {
          if (!resumeExtractionPagePersistenceService.insertExtractionPages(pages)) {
            throw new IllegalStateException("简历抽取页批量持久化失败, extractionId=" + extractionId);
          }
          requireSingleWrite(
              extractionRunPersistenceService.completeExtraction(
                  extractionId,
                  document.extractorVersion(),
                  document.route(),
                  document.pageCount(),
                  document.successfulPages(),
                  document.fallbackPages(),
                  decimal(document.printableRatio()),
                  decimal(document.coverageRatio()),
                  clock.instant()),
              "完成简历抽取运行",
              extractionId);
        });
  }

  private List<ResumeExtractionPage> createExtractionPages(
      String extractionId, ExtractedDocument document) {
    validateExtractionResult(document);
    List<ResumeExtractionPage> pages = new ArrayList<>(document.pages().size());
    for (var extractedPage : document.pages()) {
      ResumeExtractionPage page = new ResumeExtractionPage();
      page.setId(ids.newId());
      page.setExtractionRunId(extractionId);
      page.setPageNo(extractedPage.pageNo());
      page.setRoute(extractedPage.route());
      page.setExtractedText(extractedPage.text());
      page.setCharacterCount(extractedPage.characterCount());
      page.setPrintableRatio(decimal(extractedPage.printableRatio()));
      page.setQualityScore(decimal(extractedPage.qualityScore()));
      page.setContentHash(extractedPage.contentHash());
      pages.add(page);
    }
    return List.copyOf(pages);
  }

  private static void validateExtractionResult(ExtractedDocument document) {
    if (document.pages() == null
        || document.pageCount() < 1
        || document.pageCount() > MAX_RESUME_EXTRACTION_PAGES
        || document.pageCount() != document.pages().size()
        || document.successfulPages() != document.pageCount()
        || document.fallbackPages() < 0
        || document.fallbackPages() > document.successfulPages()
        || CommonUtils.isBlank(document.extractorVersion())
        || CommonUtils.isBlank(document.route())
        || !validRatio(document.printableRatio())
        || !validRatio(document.coverageRatio())) {
      throw new IllegalStateException("document extraction result is invalid");
    }
    Set<Integer> pageNumbers = new LinkedHashSet<>();
    for (var page : document.pages()) {
      if (page == null
          || page.pageNo() < 1
          || page.pageNo() > document.pageCount()
          || !pageNumbers.add(page.pageNo())
          || CommonUtils.isBlank(page.route())
          || page.text() == null
          || page.characterCount() < 0
          || !validRatio(page.printableRatio())
          || !validRatio(page.qualityScore())
          || CommonUtils.isBlank(page.contentHash())) {
        throw new IllegalStateException("document extraction page result is invalid");
      }
    }
  }

  private static boolean validRatio(double value) {
    return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
  }

  private void failExtractionAndTask(
      TaskSnapshot task,
      String extractionId,
      String errorCode,
      String safeDetail,
      boolean retryable) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          requireSingleWrite(
              extractionRunPersistenceService.failExtraction(
                  extractionId, errorCode, clock.instant()),
              "标记简历抽取运行失败",
              extractionId);
          failParseAttempt(task, errorCode, safeDetail, retryable);
        });
  }

  private void startModelRun(String modelRunId, String versionId, String extractionId, TaskSnapshot task) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          requireOwnedLease(task);
          requireSingleWrite(
              resumeModelRunPersistenceService.insertModelRun(
                  modelRunId,
                  versionId,
                  extractionId,
                  RESUME_FACT_SCHEMA_VERSION,
                  RESUME_PARSE_PROMPT_VERSION,
                  clock.instant()),
              "创建简历模型运行",
              modelRunId);
        });
  }

  /** 任务行锁覆盖启动写入，防止最终取消或失租后再创建业务运行。 */
  private void requireOwnedLease(TaskSnapshot task) {
    if (!tasks.renewLease(task)) {
      throw PlatformExceptions.conflict(AsyncTaskErrorCode.TASK_STATE_CONFLICT.getCode(), "任务租约已失效或已取消");
    }
  }

  private void failModelRunAndTask(
      TaskSnapshot task,
      String modelRunId,
      AiOutputValidationStatus validationStatus,
      String errorCode,
      ModelResult result,
      ArchiveResult archived,
      String safeDetail) {
    failModelRunAndTask(task, modelRunId, validationStatus, errorCode, result, archived, safeDetail, true);
  }

  private void failModelRunAndTask(
      TaskSnapshot task,
      String modelRunId,
      AiOutputValidationStatus validationStatus,
      String errorCode,
      ModelResult result,
      ArchiveResult archived,
      String safeDetail,
      boolean retryable) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          failModelRun(modelRunId, validationStatus, errorCode, result, archived);
          failParseAttempt(task, errorCode, safeDetail, retryable);
        });
  }

  private void failParseAttempt(TaskSnapshot task, String errorCode, String safeDetail,
      boolean retryable) {
    if (retryable) {
      tasks.failAndRetryAt(task, errorCode, safeDetail,
          TaskRetryBackoff.nextRetryAt(clock.instant(), task.attemptCount()));
    } else {
      tasks.fail(task, errorCode, safeDetail, false);
    }
  }

  private void cancelModelRunAndTask(
      TaskSnapshot task, String modelRunId, ModelResult result, ArchiveResult archived) {
    transactionTemplate.executeWithoutResult(
        ignored -> {
          failModelRun(
              modelRunId,
              AiOutputValidationStatus.VALID,
              "TASK_CANCELLED",
              result,
              archived);
          tasks.cancelled(task);
        });
  }

  private void failModelRun(
      String modelRunId,
      AiOutputValidationStatus validationStatus,
      String errorCode,
      ModelResult result,
      ArchiveResult archived) {
    requireSingleWrite(
        resumeModelRunPersistenceService.failModelRun(
            modelRunId,
            validationStatus,
            errorCode,
            result == null ? null : result.provider(),
            result == null ? null : result.modelVersion(),
            archived == null ? null : archived.objectKey(),
            archived == null ? null : archived.sha256(),
            archived == null ? null : archived.sizeBytes(),
            archived == null ? null : archived.encryptionVersion(),
            result == null ? 0 : result.inputTokens(),
            result == null ? 0 : result.outputTokens(),
            result == null ? 0 : result.durationMillis(),
            clock.instant()),
        "标记简历模型运行失败",
        modelRunId);
  }

  private void markModelRunComplete(
      String modelRunId, ModelResult result, ArchiveResult archived) {
    requireSingleWrite(
        resumeModelRunPersistenceService.completeModelRun(
            modelRunId,
            result.provider(),
            result.modelVersion(),
            archived.objectKey(),
            archived.sha256(),
            archived.sizeBytes(),
            archived.encryptionVersion(),
            result.inputTokens(),
            result.outputTokens(),
            result.durationMillis(),
            clock.instant()),
        "完成简历模型运行",
        modelRunId);
  }

  private static void requireSnapshot(FactSnapshot snapshot) {
    if (snapshot == null || CommonUtils.isBlank(snapshot.getId())) {
      throw new IllegalStateException("简历事实快照持久化后无法回读");
    }
  }

  private static void requireSingleWrite(int affectedRows, String operation, String resourceId) {
    if (affectedRows != 1) {
      throw new IllegalStateException(
          operation + "未影响唯一记录, resourceId=" + resourceId + ", affectedRows=" + affectedRows);
    }
  }

  private FactBundle bundle(FactSnapshot s) {
    return new FactBundle(
        s,
        workFactPersistenceService.findWork(s.getId()),
        projectFactPersistenceService.findProjects(s.getId()),
        educationFactPersistenceService.findEducation(s.getId()),
        skillFactPersistenceService.findSkills(s.getId()),
        simpleFactPersistenceService.findSimple(s.getId()));
  }

  private ResumeFacts toFacts(FactBundle b) {
    FactSnapshot s = b.getSnapshot();
    CandidateIdentity candidate =
        new CandidateIdentity(
            s.getFullName(),
            s.getEmail(),
            s.getPhone(),
            s.getCurrentRole(),
            s.getCity(),
            s.getAge(),
            s.getGender(),
            s.getJobIntention(),
            CandidateBirthMetadata.birthDate(s));
    List<WorkExperience> work =
        b.getWorkExperiences().stream()
            .map(
                v ->
                    new WorkExperience(
                        v.getItemKey(),
                        v.getEmployer(),
                        v.getRoleName(),
                        FactDateMetadata.read(v.getExt(), "startDateText", v.getStartDate()),
                        FactDateMetadata.read(v.getExt(), "endDateText", v.getEndDate()),
                        v.getDescriptionText(),
                        strings(v.getResponsibilitiesJson()),
                        strings(v.getAchievementsJson())))
            .toList();
    List<ProjectExperience> projects =
        b.getProjectExperiences().stream()
            .map(
                v ->
                    new ProjectExperience(
                        v.getItemKey(),
                        v.getProjectName(),
                        v.getRoleName(),
                        FactDateMetadata.read(v.getExt(), "startDateText", v.getStartDate()),
                        FactDateMetadata.read(v.getExt(), "endDateText", v.getEndDate()),
                        v.getDescriptionText(),
                        strings(v.getTechnologiesJson()),
                        strings(v.getAchievementsJson())))
            .toList();
    List<Education> educations =
        b.getEducations().stream()
            .map(
                v ->
                    new Education(
                        v.getItemKey(),
                        v.getSchool(),
                        v.getDegreeName(),
                        v.getMajor(),
                        FactDateMetadata.read(v.getExt(), "startDateText", v.getStartDate()),
                        FactDateMetadata.read(v.getExt(), "endDateText", v.getEndDate()),
                        v.getLearningMode()))
            .toList();
    List<Skill> skills =
        b.getSkills().stream()
            .map(
                v ->
                    new Skill(
                        v.getItemKey(),
                        v.getSkillName(),
                        v.getLevelName(),
                        v.getYearsValue() == null ? null : v.getYearsValue().doubleValue()))
            .toList();
    List<Credential> credentials =
        b.getSimpleFacts().stream()
            .filter(v -> ResumeFactType.CREDENTIAL.matches(v.getFactType()))
            .map(
                v ->
                    new Credential(
                        v.getItemKey(),
                        v.getNameValue(),
                        v.getIssuerValue(),
                        FactDateMetadata.read(v.getExt(), "startDateText", v.getStartDate()),
                        FactDateMetadata.read(v.getExt(), "endDateText", v.getEndDate())))
            .toList();
    List<Language> languages =
        b.getSimpleFacts().stream()
            .filter(v -> ResumeFactType.LANGUAGE.matches(v.getFactType()))
            .map(v -> new Language(v.getItemKey(), v.getNameValue(), v.getLevelValue()))
            .toList();
    List<String> industries = simpleNames(b, ResumeFactType.INDUSTRY_TAG.getCode()),
        responsibilities = simpleNames(b, ResumeFactType.RESPONSIBILITY_TAG.getCode()),
        unknown = simpleNames(b, ResumeFactType.UNKNOWN.getCode());
    return new ResumeFacts(
        candidate,
        work,
        projects,
        educations,
        skills,
        credentials,
        languages,
        industries,
        responsibilities,
        unknown);
  }

  /** 兼容旧客户端：出生信息缺失或 null 表示未编辑，不能顺带清空已有事实。 */
  private static CandidateIdentity preserveBirthDate(CandidateIdentity proposed, CandidateIdentity previous) {
    if (proposed == null || proposed.birthDate() != null || previous.birthDate() == null) return proposed;
    return new CandidateIdentity(proposed.fullName(), proposed.email(), proposed.phone(),
        proposed.currentRole(), proposed.city(), proposed.age(), proposed.gender(),
        proposed.jobIntention(), previous.birthDate());
  }

  private List<CorrectionMutationDTO> diff(JsonNode oldNode, JsonNode newNode, String pointer) {
    List<CorrectionMutationDTO> changes = new ArrayList<>();
    if (Objects.equals(oldNode, newNode)) return changes;
    if (oldNode != null && newNode != null && oldNode.isObject() && newNode.isObject()) {
      Set<String> names = new LinkedHashSet<>(oldNode.propertyNames());
      names.addAll(newNode.propertyNames());
      for (String name : names)
        changes.addAll(
            diff(oldNode.get(name), newNode.get(name), pointer + "/" + escapeJsonPointerToken(name)));
      return changes;
    }
    if (oldNode != null
        && newNode != null
        && oldNode.isArray()
        && newNode.isArray()
        && oldNode.size() == newNode.size()) {
      for (int i = 0; i < oldNode.size(); i++)
        changes.addAll(diff(oldNode.get(i), newNode.get(i), pointer + "/" + i));
      return changes;
    }
    changes.add(
        new CorrectionMutationDTO(
            pointer,
            oldNode == null ? json.nullNode() : oldNode,
            newNode == null ? json.nullNode() : newNode));
    return changes;
  }

  private static List<String> affectedCapabilities(List<CorrectionMutationDTO> changes) {
    Set<String> values = new LinkedHashSet<>();
    for (var change : changes) {
      String p = change.getJsonPointer();
      values.add("SEARCH");
      if (p.startsWith("/candidate/fullName")
          || p.startsWith("/candidate/email")
          || p.startsWith("/candidate/phone")) values.add("IDENTITY_RESOLUTION");
      if (p.startsWith("/candidate/age")
          || p.startsWith("/candidate/birthDate")
          || p.startsWith("/candidate/gender")
          || p.startsWith("/educations")
          || p.startsWith("/credentials")
          || p.startsWith("/languages")) values.add("CONTROLLED_CONDITIONS");
      if (p.startsWith("/candidate/currentRole")
          || p.startsWith("/workExperiences")
          || p.startsWith("/projectExperiences")
          || p.startsWith("/skills")
          || p.startsWith("/industryTags")
          || p.startsWith("/responsibilityTags")) values.add("MATCHING");
    }
    return List.copyOf(values);
  }

  private List<String> strings(String raw) {
    JsonNode node = json.readTree(raw);
    if (node == null || !node.isArray()) return List.of();
    List<String> values = new ArrayList<>();
    for (JsonNode child : node) values.add(child.asString());
    return List.copyOf(values);
  }

  private static List<String> simpleNames(FactBundle b, String type) {
    return b.getSimpleFacts().stream()
        .filter(v -> type.equals(v.getFactType()))
        .map(SimpleFact::getNameValue)
        .toList();
  }



  private static String escapeJsonPointerToken(String value) {
    return value.replace("~", "~0").replace("/", "~1");
  }

  private ResumeResourceEntity requireResource(String id) {
    ResumeResourceEntity resource = persistence.findResumeResource(id);
    if (resource == null)
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "RESUME_VERSION_NOT_FOUND", "简历版本不存在或不可用", false);
    return resource;
  }

  private ResumeResourceEntity requireReadableResource(String id) {
    ResumeResourceEntity resource = requireResource(id);
    authorization.currentActor();
    // 共享准入按实时业务事实判断，覆盖全部简历版本及历史事实；离库后恢复原范围。
    if (!library.eligibleCandidateIds(List.of(resource.getCandidateId())).contains(resource.getCandidateId())) {
      authorization.requireAccess("talent.read", target(resource));
    }
    return resource;
  }

  private static AuthorizationTarget target(ResumeResourceEntity resource) {
    return new AuthorizationTarget(
        "CANDIDATE",
        resource.getCandidateId(),
        resource.getOwnerUserId(),
        resource.getOwnerDepartmentId(),
        Set.of());
  }

  private static BigDecimal decimal(double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
  }

  private static String extractionCode(IOException ex) {
    return ResumeExtractionErrorCode.classify(ex);
  }

  private static String extractionFailureDetail(String code) {
    if (ResumeExtractionErrorCode.DOCUMENT_PAGE_RENDER_LIMIT.matches(code)) {
      return "PDF 图片或页面超出安全识别范围，请压缩图片或重新导出带文字层的 PDF 后上传";
    }
    if (ResumeExtractionErrorCode.DOCUMENT_CONTAINER_LIMIT.matches(code)
        || ResumeExtractionErrorCode.DOCUMENT_CONTAINER_INVALID.matches(code)) {
      return "文档内容或内部结构无法安全处理，请重新导出简历后上传";
    }
    if (ResumeExtractionErrorCode.DOCUMENT_PAGE_LIMIT.matches(code)
        || ResumeExtractionErrorCode.DOCUMENT_TEXT_LIMIT.matches(code)) {
      return "简历页数或文字内容超出安全上限，请精简内容或重新导出后上传";
    }
    return "简历正文提取失败，请检查文件质量或稍后重试";
  }

  private static boolean retryableExtraction(IOException ex) {
    String code = extractionCode(ex);
    return ResumeExtractionErrorCode.allowsWholeDocumentFallback(code);
  }

  private static String fingerprint(String... values) {
    return Sha256Utils.fingerprintStrings("", 0, values);
  }
}

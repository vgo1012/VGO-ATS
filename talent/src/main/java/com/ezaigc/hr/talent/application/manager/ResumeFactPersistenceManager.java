package com.ezaigc.hr.talent.application.manager;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.talent.api.ResumeDateValue;
import com.ezaigc.hr.talent.domain.service.FactDateMetadata;
import com.ezaigc.hr.talent.domain.service.CandidateBirthMetadata;
import com.ezaigc.hr.talent.service.support.FactOrderMetadata;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.talent.api.enums.ResumeFactErrorCode;
import com.ezaigc.hr.talent.enums.ResumeFactSnapshotSource;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Credential;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Education;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Language;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ProjectExperience;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Skill;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.WorkExperience;
import com.ezaigc.hr.talent.domain.dto.CorrectionMutationDTO;
import com.ezaigc.hr.talent.domain.dto.CorrectionPersistenceResultDTO;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.enums.ResumeFactType;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.FactCorrectionChangePersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParseSnapshotPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Service
public class ResumeFactPersistenceManager {

  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Resource private ObjectMapper json;

  @Resource private ResumeParsingPersistenceService persistence;
  @Resource private ResumeVersionPersistenceService resumeVersions;
  @Resource private EducationFactPersistenceService educationFactPersistenceService;
  @Resource private FactCorrectionChangePersistenceService factCorrectionChangePersistenceService;
  @Resource private ProjectFactPersistenceService projectFactPersistenceService;
  @Resource private ResumeParseSnapshotPersistenceService resumeParseSnapshotPersistenceService;
  @Resource private SimpleFactPersistenceService simpleFactPersistenceService;
  @Resource private SkillFactPersistenceService skillFactPersistenceService;
  @Resource private TalentProcessEventPersistenceService talentProcessEventPersistenceService;
  @Resource private WorkFactPersistenceService workFactPersistenceService;
  @Resource private ResumeSearchProjectionRecorder searchProjection;

  @Transactional
  public FactSnapshot storeAiFacts(
      String resumeVersionId,
      String extractionId,
      String modelRunId,
      String schemaVersion,
      String promptVersion,
      String cacheKey,
      ResumeFacts facts) {
    return storeAiFacts(resumeVersionId, extractionId, modelRunId, schemaVersion,
        promptVersion, cacheKey, facts, LocalDate.now(clock));
  }

  /** 归档共享结果时保留原年龄日期，旧缓存没有日期时不得冒充今天。 */
  @Transactional
  public FactSnapshot storeAiFacts(
      String resumeVersionId,
      String extractionId,
      String modelRunId,
      String schemaVersion,
      String promptVersion,
      String cacheKey,
      ResumeFacts facts,
      LocalDate ageAsOf) {
    lockResumeVersion(resumeVersionId);
    FactSnapshot previous = persistence.findCurrentFactSnapshotForUpdate(resumeVersionId);
    if (previous != null && ResumeFactSnapshotSource.HUMAN_CORRECTION.matches(previous.getSourceType())) {
      throw PlatformExceptions.conflict(
          ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getCode(), ResumeFactErrorCode.HUMAN_CORRECTION_CURRENT.getDesc());
    }
    String existingParse = resumeParseSnapshotPersistenceService.findParseSnapshotByCache(cacheKey);
    if (existingParse != null) {
      FactSnapshot existing = persistence.findFactSnapshotByParse(existingParse);
      if (existing != null) {
        if (!resumeVersionId.equals(existing.getResumeVersionId())
            || previous == null
            || !previous.getId().equals(existing.getId())
            || !ResumeFactSnapshotSource.AI_PARSE.matches(existing.getSourceType())) {
          throw PlatformExceptions.conflict(
              ResumeFactErrorCode.FACT_CACHE_LINEAGE_CONFLICT.getCode(),
              ResumeFactErrorCode.FACT_CACHE_LINEAGE_CONFLICT.getDesc());
        }
        searchProjection.factChanged(resumeVersionId, existing.getId(), null);
        return existing;
      }
    }
    var now = clock.instant();
    String parseId = ids.newId();
    resumeParseSnapshotPersistenceService.insertParseSnapshot(
        parseId,
        resumeVersionId,
        extractionId,
        modelRunId,
        schemaVersion,
        promptVersion,
        cacheKey,
        now);
    persistence.clearCurrentFact(resumeVersionId, PlatformActorConstants.SYSTEM_ACTOR_ID, now);
    String snapshotId = ids.newId();
    int version = previous == null ? 1 : previous.getSnapshotVersion() + 1;
    CandidateIdentity candidate = facts.candidate();
    persistence.insertFactSnapshot(
        snapshotId,
        resumeVersionId,
        parseId,
        previous == null ? null : previous.getId(),
        version,
        ResumeFactSnapshotSource.AI_PARSE.getCode(),
        candidate.fullName(),
        candidate.email(),
        candidate.phone(),
        candidate.currentRole(),
        candidate.city(),
        candidate.age(),
        candidate.gender(),
        candidate.jobIntention(),
        CandidateBirthMetadata.write(candidate.birthDate(), candidate.age() == null ? null : ageAsOf),
        null,
        null,
        now);
    insertChildren(snapshotId, facts);
    searchProjection.factChanged(resumeVersionId, snapshotId, null);
    return persistence.findFactSnapshot(snapshotId);
  }

  @Transactional
  public CorrectionPersistenceResultDTO storeCorrection(
      String resumeVersionId,
      String baseSnapshotId,
      String actorId,
      String reason,
      String sourceType,
      List<String> capabilities,
      List<CorrectionMutationDTO> mutations,
      ResumeFacts facts) {
    lockResumeVersion(resumeVersionId);
    FactSnapshot previous = persistence.findCurrentFactSnapshotForUpdate(resumeVersionId);
    if (previous == null || !previous.getId().equals(baseSnapshotId))
      throw new PlatformException(
          HttpStatus.CONFLICT, "FACT_SNAPSHOT_CHANGED", "事实快照已变化，请刷新后重新提交纠正", true);
    var now = clock.instant();
    persistence.clearCurrentFact(resumeVersionId, actorId, now);
    String snapshotId = ids.newId();
    CandidateIdentity candidate = facts.candidate();
    persistence.insertFactSnapshot(
        snapshotId,
        resumeVersionId,
        previous.getParseSnapshotId(),
        previous.getId(),
        previous.getSnapshotVersion() + 1,
        ResumeFactSnapshotSource.HUMAN_CORRECTION.getCode(),
        candidate.fullName(),
        candidate.email(),
        candidate.phone(),
        candidate.currentRole(),
        candidate.city(),
        candidate.age(),
        candidate.gender(),
        candidate.jobIntention(),
        CandidateBirthMetadata.write(candidate.birthDate(), candidate.age() == null ? null
            : Objects.equals(candidate.age(), previous.getAge())
                ? CandidateBirthMetadata.ageAsOf(previous) : now.atZone(clock.getZone()).toLocalDate()),
        actorId,
        reason,
        now);
    insertChildren(snapshotId, facts);
    String correctionId = ids.newId();
    persistence.insertCorrection(
        correctionId,
        resumeVersionId,
        previous.getId(),
        snapshotId,
        actorId,
        reason,
        write(capabilities),
        now);
    insertCorrectionChanges(correctionId, sourceType, mutations);
    talentProcessEventPersistenceService.insertCorrectionEvent(ids.newId(), actorId, resumeVersionId, reason, now);
    searchProjection.factChanged(resumeVersionId, snapshotId, actorId);
    FactSnapshot snapshot = persistence.findFactSnapshot(snapshotId);
    return new CorrectionPersistenceResultDTO(
        correctionId, snapshot.getId(), snapshot.getSnapshotVersion(), capabilities);
  }

  private void lockResumeVersion(String resumeVersionId) {
    if (resumeVersions.lockForRouting(resumeVersionId) == null) {
      throw PlatformExceptions.conflict("RESUME_VERSION_CHANGED", "简历版本已不可用，请刷新后重试");
    }
  }

  private void insertChildren(String snapshotId, ResumeFacts facts) {
    Objects.requireNonNull(snapshotId, "snapshotId must not be null");
    Objects.requireNonNull(facts, "facts must not be null");

    // 子事实上限较高，必须按类型批量写入，避免长事务中产生数千次数据库往返。
    requireBatchSaved(
        workFactPersistenceService.insertWorkFacts(toWorkFacts(snapshotId, facts.workExperiences())),
        "work facts");
    requireBatchSaved(
        projectFactPersistenceService.insertProjectFacts(
            toProjectFacts(snapshotId, facts.projectExperiences())),
        "project facts");
    requireBatchSaved(
        educationFactPersistenceService.insertEducationFacts(
            toEducationFacts(snapshotId, facts.educations())),
        "education facts");
    requireBatchSaved(
        skillFactPersistenceService.insertSkillFacts(toSkillFacts(snapshotId, facts.skills())),
        "skill facts");
    requireBatchSaved(
        simpleFactPersistenceService.insertSimpleFacts(toSimpleFacts(snapshotId, facts)),
        "simple facts");
  }

  /** 一次纠错的字段差异使用一个受控批次写入，避免在事务内逐条往返数据库。 */
  private void insertCorrectionChanges(
      String correctionId, String sourceType, List<CorrectionMutationDTO> mutations) {
    Objects.requireNonNull(correctionId, "correctionId must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    Objects.requireNonNull(mutations, "mutations must not be null");
    List<FactCorrectionChange> changes = new ArrayList<>(mutations.size());
    for (CorrectionMutationDTO mutation : mutations) {
      Objects.requireNonNull(mutation, "correction mutation must not be null");
      FactCorrectionChange change = new FactCorrectionChange();
      change.setId(ids.newId());
      change.setCorrectionId(correctionId);
      change.setJsonPointer(mutation.getJsonPointer());
      change.setOldValueJson(writeNode(mutation.getOldValue()));
      change.setNewValueJson(writeNode(mutation.getNewValue()));
      change.setSourceType(sourceType);
      changes.add(change);
    }
    requireBatchSaved(
        factCorrectionChangePersistenceService.insertCorrectionChanges(changes),
        "fact correction changes");
  }

  private List<WorkFact> toWorkFacts(String snapshotId, List<WorkExperience> values) {
    List<WorkFact> result = new ArrayList<>(values.size());
    for (WorkExperience value : values) {
      result.add(
          FactDateMetadata.capture(new WorkFact(
              ids.newId(),
              snapshotId,
              value.id(),
              value.employer(),
              value.role(),
              date(value.startDate()),
              date(value.endDate()),
              value.description(),
              write(value.responsibilities()),
              write(value.achievements())), value.startDate(), value.endDate()));
    }
    return FactOrderMetadata.capture(result);
  }

  private List<ProjectFact> toProjectFacts(String snapshotId, List<ProjectExperience> values) {
    List<ProjectFact> result = new ArrayList<>(values.size());
    for (ProjectExperience value : values) {
      result.add(
          FactDateMetadata.capture(new ProjectFact(
              ids.newId(),
              snapshotId,
              value.id(),
              value.projectName(),
              value.role(),
              date(value.startDate()),
              date(value.endDate()),
              value.description(),
              write(value.technologies()),
              write(value.achievements())), value.startDate(), value.endDate()));
    }
    return FactOrderMetadata.capture(result);
  }

  private List<EducationFact> toEducationFacts(String snapshotId, List<Education> values) {
    List<EducationFact> result = new ArrayList<>(values.size());
    for (Education value : values) {
      result.add(
          FactDateMetadata.capture(new EducationFact(
              ids.newId(),
              snapshotId,
              value.id(),
              value.school(),
              value.degree(),
              value.major(),
              date(value.startDate()),
              date(value.endDate()),
              value.learningMode()), value.startDate(), value.endDate()));
    }
    return FactOrderMetadata.capture(result);
  }

  private List<SkillFact> toSkillFacts(String snapshotId, List<Skill> values) {
    List<SkillFact> result = new ArrayList<>(values.size());
    for (Skill value : values) {
      result.add(
          new SkillFact(
              ids.newId(),
              snapshotId,
              value.id(),
              value.name(),
              value.level(),
              value.years() == null ? null : BigDecimal.valueOf(value.years())));
    }
    return FactOrderMetadata.capture(result);
  }

  private List<SimpleFact> toSimpleFacts(String snapshotId, ResumeFacts facts) {
    int expectedSize =
        facts.credentials().size()
            + facts.languages().size()
            + facts.industryTags().size()
            + facts.responsibilityTags().size()
            + facts.unknownItems().size();
    List<SimpleFact> result = new ArrayList<>(expectedSize);
    for (Credential value : facts.credentials()) {
      result.add(
          FactDateMetadata.capture(new SimpleFact(
              ids.newId(),
              snapshotId,
              ResumeFactType.CREDENTIAL.getCode(),
              value.id(),
              value.name(),
              null,
              value.issuer(),
              date(value.issuedDate()),
              date(value.expiryDate())), value.issuedDate(), value.expiryDate()));
    }
    for (Language value : facts.languages()) {
      result.add(
          new SimpleFact(
              ids.newId(),
              snapshotId,
              ResumeFactType.LANGUAGE.getCode(),
              value.id(),
              value.name(),
              value.level(),
              null,
              null,
              null));
    }
    appendStringFacts(
        snapshotId, ResumeFactType.INDUSTRY_TAG.getCode(), facts.industryTags(), result);
    appendStringFacts(
        snapshotId,
        ResumeFactType.RESPONSIBILITY_TAG.getCode(),
        facts.responsibilityTags(),
        result);
    appendStringFacts(snapshotId, ResumeFactType.UNKNOWN.getCode(), facts.unknownItems(), result);
    return FactOrderMetadata.capture(result, SimpleFact::getFactType);
  }

  private void appendStringFacts(
      String snapshotId, String type, List<String> values, List<SimpleFact> target) {
    int index = 0;
    for (String value : values) {
      target.add(
          new SimpleFact(
              ids.newId(),
              snapshotId,
              type,
              type.toLowerCase(Locale.ROOT) + "_" + (++index),
              value,
              null,
              null,
              null,
              null));
    }
  }

  private static void requireBatchSaved(boolean saved, String factType) {
    if (!saved) {
      throw new IllegalStateException(factType + " batch persistence failed");
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value == null ? List.of() : value);
    } catch (RuntimeException ex) {
      throw new IllegalStateException("validated facts could not be serialized", ex);
    }
  }

  private String writeNode(JsonNode value) {
    try {
      return json.writeValueAsString(value);
    } catch (RuntimeException ex) {
      throw new IllegalStateException("correction value could not be serialized", ex);
    }
  }

  private static LocalDate date(String value) {
    var parsed = ResumeDateValue.parse(value);
    return parsed == null ? null : parsed.completeDate();
  }
}

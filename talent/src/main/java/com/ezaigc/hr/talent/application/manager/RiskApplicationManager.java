package com.ezaigc.hr.talent.application.manager;

import static com.ezaigc.hr.common.web.request.RequiredText.required;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.CompanyBlacklistQueryService;
import com.ezaigc.hr.talent.api.EducationApplicationAccessService;
import com.ezaigc.hr.talent.application.assembler.RiskAssembler;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistView;
import com.ezaigc.hr.talent.domain.response.AffectedWorkflowResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistResponse;
import com.ezaigc.hr.talent.domain.response.CompanyBlacklistHistoryResponse;
import com.ezaigc.hr.talent.domain.response.EducationVerificationResponse;
import com.ezaigc.hr.talent.domain.response.RestrictionSummaryResponse;
import com.ezaigc.hr.talent.enums.EducationAttendanceMode;
import com.ezaigc.hr.talent.enums.EducationRecordType;
import com.ezaigc.hr.talent.enums.EducationVerificationStatus;
import com.ezaigc.hr.talent.enums.CompanyBlacklistStatus;
import com.ezaigc.hr.talent.service.CompanyBlacklistCurrentPersistenceService;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.EducationRecordPersistenceService;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

/** 风险应用服务，负责用例编排、权限校验和事务边界。 */
@Service
public class RiskApplicationManager implements CompanyBlacklistQueryService {

  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Resource private ObjectProvider<EducationApplicationAccessService> applicationAccess;

  @Resource private RiskPersistenceService persistence;
  @Resource private CompanyBlacklistCurrentPersistenceService companyBlacklistCurrentPersistenceService;
  @Resource private EducationRecordPersistenceService educationRecordPersistenceService;
  @Resource private AuthorizationService authorization;
  @Resource private CandidateDirectoryService candidates;
  @Resource private CandidatePersistenceService candidatePersistence;
  @Resource private RiskAssembler assembler;
  @Resource private CursorCodec cursorCodec;
  @Resource private CandidateBatchApplicationManager candidateBatch;
  @Resource @Lazy private CandidateLibraryQueryService library;

  /** 只返回有权读取且带有效标签的候选人，不公开其他候选人的风险存在性。 */
  @Transactional(readOnly = true)
  public List<String> blacklistTags(List<String> candidateIds) {
    var permitted = candidateIds;
    try {
      authorization.requirePermission("risk.read");
    } catch (PlatformException denied) {
      if (denied.status() != HttpStatus.FORBIDDEN) throw denied;
      permitted = List.copyOf(library.eligibleCandidateIds(candidateIds));
      if (permitted.isEmpty()) throw denied;
    }
    var readable = candidateBatch.read(permitted).stream().map(item -> item.getId()).toList();
    return readable.isEmpty() ? List.of() : persistence.blacklistTags(readable, clock.instant());
  }

  @Transactional(readOnly = true)
  public boolean isCompanyBlacklisted(String candidateId) {
    return companyBlacklistCurrentPersistenceService.isBlacklisted(candidateId) > 0;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockCandidateForCommand(String candidateId) {
    if (candidatePersistence.lockCandidate(candidateId) == null) {
      throw PlatformExceptions.notFound("CANDIDATE_NOT_FOUND", "候选人不存在");
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireRecruitmentAllowed(String candidateId) {
    // 新事实检查在幂等回放之后执行；纯候选人锁继续允许负向操作和历史回放。
    candidates.lockActive(candidateId);
    CompanyBlacklistCurrent current = companyBlacklistCurrentPersistenceService.lockCurrent(candidateId);
    if (current != null && current.isActive()) {
      throw conflict("COMPANY_BLACKLIST_BLOCKED", "候选人有生效公司黑名单，不能继续招聘推进");
    }
  }

  /** 创建黑名单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CompanyBlacklistResponse createBlacklist(
      String candidateId,
      String reasonCode,
      String detail,
      String source,
      String evidence,
      Instant occurredAt) {
    authorization.requirePermission("risk.manage");
    lockCandidateForCommand(candidateId);
    candidates.lockActive(candidateId);
    candidates.requireActive(candidateId);
    CompanyBlacklistCurrent current = companyBlacklistCurrentPersistenceService.lockCurrent(candidateId);
    if (current != null && current.isActive())
      throw conflict("COMPANY_BLACKLIST_EXISTS", "该候选人已有生效公司黑名单");
    return assembler.toResponse(
        appendBlacklist(
            candidateId,
            "ACTIVATION",
            CompanyBlacklistStatus.ACTIVE.getCode(),
            reasonCode,
            detail,
            source,
            evidence,
            null,
            current == null ? null : current.getRecordId(),
            true,
            occurredAt));
  }

  /** 更正黑名单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CompanyBlacklistResponse correctBlacklist(
      String recordId,
      String reasonCode,
      String detail,
      String source,
      String evidence,
      Instant occurredAt) {
    authorization.requirePermission("risk.manage");
    CompanyBlacklistRecord prior = requiredBlacklist(recordId);
    lockCandidateForCommand(prior.getCandidateId());
    CompanyBlacklistCurrent current = companyBlacklistCurrentPersistenceService.lockCurrent(prior.getCandidateId());
    if (current == null || !current.getRecordId().equals(prior.getId()) || !current.isActive())
      throw conflict("BLACKLIST_RECORD_NOT_CURRENT", "只能更正当前生效记录");
    return assembler.toResponse(
        appendBlacklist(
            prior.getCandidateId(),
            "CORRECTION",
            CompanyBlacklistStatus.CORRECTED.getCode(),
            reasonCode,
            detail,
            source,
            evidence,
            prior.getId(),
            prior.getId(),
            true,
            occurredAt));
  }

  /** 停用黑名单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CompanyBlacklistResponse disableBlacklist(String recordId, String reason) {
    authorization.requirePermission("risk.manage");
    CompanyBlacklistRecord prior = requiredBlacklist(recordId);
    lockCandidateForCommand(prior.getCandidateId());
    CompanyBlacklistCurrent current = companyBlacklistCurrentPersistenceService.lockCurrent(prior.getCandidateId());
    if (current == null || !current.getRecordId().equals(prior.getId()) || !current.isActive())
      throw conflict("BLACKLIST_RECORD_NOT_CURRENT", "只能停用当前生效记录");
    return assembler.toResponse(
        appendBlacklist(
            prior.getCandidateId(),
            "DISABLE",
            CompanyBlacklistStatus.DISABLED.getCode(),
            CompanyBlacklistStatus.DISABLED.getCode(),
            reason,
            "MANUAL",
            prior.getEvidenceReference(),
            prior.getId(),
            prior.getId(),
            false,
            clock.instant()));
  }

  @Transactional(readOnly = true)
  public CompanyBlacklistHistoryResponse blacklistHistory(String candidateId, String cursor, Integer requestedLimit) {
    requireRiskRead(candidateId);
    candidates.requireActive(candidateId);
    int limit = PageLimits.resolve(requestedLimit, 20, 200);
    String scope = CursorScopes.scope("talent.blacklist-history", authorization.currentActor().getUserId(), candidateId);
    StableCursor before = StableCursor.decode(cursorCodec, scope, cursor);
    var page = CursorPages.page(persistence.blacklistHistory(candidateId,
        before == null ? null : before.getSortTime(), before == null ? null : before.getId(), limit + 1),
        limit, cursorCodec, scope, assembler::toResponse, CompanyBlacklistRecord::getCreateTime, CompanyBlacklistRecord::getId);
    CompanyBlacklistRecord current = persistence.currentBlacklist(candidateId);
    return new CompanyBlacklistHistoryResponse(current == null ? null : assembler.toResponse(current),
        isCompanyBlacklisted(candidateId), page);
  }

  @Transactional(readOnly = true)
  public CursorPage<AffectedWorkflowResponse> affectedPage(String recordId, String cursor, Integer requestedLimit) {
    CompanyBlacklistRecord record = requiredBlacklist(recordId);
    requireRiskRead(record.getCandidateId());
    int limit = PageLimits.resolve(requestedLimit, 20, 200);
    String scope = CursorScopes.scope("talent.blacklist-affected", authorization.currentActor().getUserId(), recordId);
    StableCursor before = StableCursor.decode(cursorCodec, scope, cursor);
    return CursorPages.page(persistence.affectedPage(record.getCandidateId(),
        before == null ? null : before.getSortTime(), before == null ? null : before.getId(), limit + 1),
        limit, cursorCodec, scope, assembler::toResponse, AffectedWorkflow::getUpdateTime, AffectedWorkflow::getResourceId);
  }

  @Transactional(readOnly = true)
  public List<AffectedWorkflowResponse> affected(String recordId, int limit) {
    CompanyBlacklistRecord record = requiredBlacklist(recordId);
    requireRiskRead(record.getCandidateId());
    return assembler.toAffectedResponses(
        persistence.affected(
            record.getCandidateId(), Math.clamp(limit, 1, 200)));
  }

  @Transactional(readOnly = true)
  public List<RestrictionSummaryResponse> restrictions(String candidateId) {
    requireRiskRead(candidateId);
    candidates.requireActive(candidateId);
    return assembler.toRestrictionResponses(persistence.restrictions(candidateId, clock.instant()));
  }

  @Transactional
  public EducationVerificationResponse education(
      String applicationId,
      String candidateId,
      String educationId,
      String type,
      String verification,
      String mode,
      String source,
      String evidence,
      String note,
      String correctsId,
      Instant occurredAt) {
    authorization.requirePermission("education.verify");
    candidates.lockActive(candidateId);
    EducationApplicationAccessService access = applicationAccess.getIfAvailable();
    if (access == null) throw conflict("APPLICATION_ACCESS_UNAVAILABLE", "招聘活动权限校验不可用");
    access.requireEducationAccess(applicationId, candidateId);
    String v = CommonUtils.upperOrEmpty(verification), m = CommonUtils.upperOrEmpty(mode), t = CommonUtils.upperOrEmpty(type);
    if (!EducationVerificationStatus.supports(v)
        || !EducationAttendanceMode.supports(m)
        || !EducationRecordType.supports(t))
      throw invalid("EDUCATION_RECORD_INVALID", "学历核验状态、学习形式或记录类型无效");
    if (EducationRecordType.CORRECTION.matches(t) && CommonUtils.stripToNull(correctsId) == null)
      throw invalid("EDUCATION_CORRECTION_TARGET_REQUIRED", "学历更正必须引用原记录");
    String id = ids.newId();
    var actor = authorization.currentActor();
    Instant now = clock.instant(), when = occurredAt == null ? now : occurredAt;
    educationRecordPersistenceService.insertEducation(
        id,
        candidateId,
        required(educationId, "educationFactId", 26),
        applicationId,
        t,
        v,
        m,
        required(source, "sourceChannel", 40),
        CommonUtils.stripToNull(evidence),
        CommonUtils.stripToNull(note),
        CommonUtils.stripToNull(correctsId),
        actor.getUserId(),
        when,
        now);
    return assembler.toResponse(educationRecordPersistenceService.findEducation(id));
  }

  @Transactional(readOnly = true)
  public List<EducationVerificationResponse> education(String candidateId, int limit) {
    requireRiskRead(candidateId);
    return assembler.toEducationResponses(
        educationRecordPersistenceService.education(candidateId, Math.clamp(limit, 1, 200)));
  }

  private void requireRiskRead(String candidateId) {
    authorization.currentActor();
    try {
      authorization.requirePermission("risk.read");
    } catch (PlatformException denied) {
      if (denied.status() != HttpStatus.FORBIDDEN
          || !library.eligibleCandidateIds(List.of(candidateId)).contains(candidateId)) throw denied;
    }
  }

  private CompanyBlacklistView appendBlacklist(
      String candidateId,
      String type,
      String status,
      String reasonCode,
      String detail,
      String source,
      String evidence,
      String corrects,
      String expectedRecordId,
      boolean active,
      Instant occurredAt) {
    String id = ids.newId();
    var actor = authorization.currentActor();
    Instant now = clock.instant(), when = occurredAt == null ? now : occurredAt;
    persistence.insertBlacklist(
        id,
        candidateId,
        type,
        status,
        required(reasonCode, "reasonCode", 60),
        required(detail, "reasonDetail", 1000),
        required(source, "sourceChannel", 40),
        required(evidence, "evidenceReference", 1000),
        corrects,
        actor.getUserId(),
        when,
        now);
    if (companyBlacklistCurrentPersistenceService.projectBlacklist(
        candidateId, expectedRecordId, id, active, actor.getUserId(), now) != 1) {
      throw conflict(corrects == null ? "COMPANY_BLACKLIST_EXISTS" : "BLACKLIST_RECORD_NOT_CURRENT",
          "公司黑名单已被修改，请刷新后重试");
    }
    return new CompanyBlacklistView(
        persistence.findBlacklist(id),
        persistence.countActiveApplications(candidateId)
            + persistence.countActiveSubmissions(candidateId));
  }

  private CompanyBlacklistRecord requiredBlacklist(String id) {
    CompanyBlacklistRecord value = persistence.findBlacklist(id);
    if (value == null)
      throw new PlatformException(
          HttpStatus.NOT_FOUND, "BLACKLIST_RECORD_NOT_FOUND", "黑名单记录不存在", false);
    return value;
  }

  private static PlatformException invalid(String code, String detail) {
    return new PlatformException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, false);
  }

  private static PlatformException conflict(String code, String detail) {
    return new PlatformException(HttpStatus.CONFLICT, code, detail, false);
  }
}

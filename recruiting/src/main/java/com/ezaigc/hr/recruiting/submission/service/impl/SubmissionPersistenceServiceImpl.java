package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientSubmission;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionDeliveryStatus;
import com.ezaigc.hr.recruiting.submission.enums.SubmissionProcessStatus;
import com.ezaigc.hr.recruiting.submission.mapper.SubmissionMapper;
import com.ezaigc.hr.recruiting.submission.service.SubmissionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 客户推荐持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation; permissions and transactions stay in application managers.
 */
@Service
public class SubmissionPersistenceServiceImpl
    extends ServiceImpl<SubmissionMapper, ClientSubmission>
    implements SubmissionPersistenceService {
  @Override
  public String findCandidateId(String id) {
    ClientSubmission value = baseMapper.selectOne(Wrappers.lambdaQuery(ClientSubmission.class)
        .select(ClientSubmission::getCandidateId).eq(ClientSubmission::getId, id));
    return value == null ? null : value.getCandidateId();
  }

  /** 新增客户推荐。 */
  @Override
  public int insertSubmission(
      String id,
      String applicationId,
      String candidateId,
      String demandId,
      String clientId,
      String projectId,
      String positionId,
      String jdId,
      String jdVersionId,
      String resumeVersionId,
      String ownerUserId,
      String departmentId,
      String actorId,
      Instant now) {
    ClientSubmission submission = new ClientSubmission();
    submission.setId(id);
    submission.setApplicationId(applicationId);
    submission.setCandidateId(candidateId);
    submission.setDemandId(demandId);
    submission.setClientId(clientId);
    submission.setProjectId(projectId);
    submission.setPositionId(positionId);
    submission.setJdId(jdId);
    submission.setJdVersionId(jdVersionId);
    submission.setResumeVersionId(resumeVersionId);
    submission.setDeliveryStatus(SubmissionDeliveryStatus.DRAFT.getCode());
    submission.setProcessStatus(SubmissionProcessStatus.NOT_STARTED.getCode());
    submission.setOwnerUserId(ownerUserId);
    submission.setOwnerDepartmentId(departmentId);
    submission.setCreateId(actorId);
    submission.setVersion(0L);
    submission.setCreateTime(now);
    submission.setUpdateId(actorId);
    submission.setUpdateTime(now);
    return baseMapper.insert(submission);
  }

  @Override
  public ClientSubmission find(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public List<ClientSubmission> list(
      String applicationId,
      String clientId,
      String jdId,
      String deliveryStatus,
      String processStatus,
      String ownerUserId,
      Instant cursorTime,
      String cursorId,
      int limit) {
    var query =
        Wrappers.lambdaQuery(ClientSubmission.class)
            .eq(applicationId != null, ClientSubmission::getApplicationId, applicationId)
            .eq(clientId != null, ClientSubmission::getClientId, clientId)
            .eq(jdId != null, ClientSubmission::getJdId, jdId)
            .eq(deliveryStatus != null, ClientSubmission::getDeliveryStatus, deliveryStatus)
            .eq(processStatus != null, ClientSubmission::getProcessStatus, processStatus)
            .eq(ownerUserId != null, ClientSubmission::getOwnerUserId, ownerUserId)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(ClientSubmission::getUpdateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(ClientSubmission::getUpdateTime, cursorTime)
                                    .lt(ClientSubmission::getId, cursorId)))
            .orderByDesc(ClientSubmission::getUpdateTime, ClientSubmission::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  /** 标记发送。 */
  @Override
  public int markSent(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ClientSubmission.class)
            .set(ClientSubmission::getDeliveryStatus, SubmissionDeliveryStatus.SENT.getCode())
            .set(ClientSubmission::getProcessStatus, SubmissionProcessStatus.SCREENING.getCode())
            .set(ClientSubmission::getSentAt, now)
            .set(ClientSubmission::getUpdateId, actorId)
            .set(ClientSubmission::getUpdateTime, now)
            .eq(ClientSubmission::getId, id)
            .eq(ClientSubmission::getDeliveryStatus, SubmissionDeliveryStatus.DRAFT.getCode())
            .eq(ClientSubmission::getVersion, version)
            .setIncrBy(ClientSubmission::getVersion, 1));
  }

  /** 更新处理。 */
  @Override
  public int updateProcess(String id, String status, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ClientSubmission.class)
            .set(ClientSubmission::getProcessStatus, status)
            .set(ClientSubmission::getUpdateId, actorId)
            .set(ClientSubmission::getUpdateTime, now)
            .eq(ClientSubmission::getId, id)
            .eq(ClientSubmission::getDeliveryStatus, SubmissionDeliveryStatus.SENT.getCode())
            .notIn(
                ClientSubmission::getProcessStatus,
                SubmissionProcessStatus.ACCEPTED.getCode(),
                SubmissionProcessStatus.REJECTED.getCode(),
                SubmissionProcessStatus.EXPIRED.getCode())
            .eq(ClientSubmission::getVersion, version)
            .setIncrBy(ClientSubmission::getVersion, 1));
  }

  @Override
  public int withdraw(String id, String reason, String actorId, long version, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ClientSubmission.class)
            .set(
                ClientSubmission::getDeliveryStatus,
                SubmissionDeliveryStatus.WITHDRAWN.getCode())
            .set(ClientSubmission::getWithdrawnAt, now)
            .set(ClientSubmission::getWithdrawnByUserId, actorId)
            .set(ClientSubmission::getWithdrawalReason, reason)
            .set(ClientSubmission::getUpdateId, actorId)
            .set(ClientSubmission::getUpdateTime, now)
            .eq(ClientSubmission::getId, id)
            .eq(ClientSubmission::getDeliveryStatus, SubmissionDeliveryStatus.SENT.getCode())
            .in(
                ClientSubmission::getProcessStatus,
                SubmissionProcessStatus.SCREENING.getCode(),
                SubmissionProcessStatus.INTERVIEWING.getCode())
            .eq(ClientSubmission::getVersion, version)
            .setIncrBy(ClientSubmission::getVersion, 1));
  }

  @Override
  public ClientSubmission lock(String id) {
    return baseMapper.lock(id);
  }

  @Override
  public int insertRestrictionProjection(String clientId, String candidateId, String recordId,
      boolean active, Instant expiresAt, String actorId, Instant now) {
    return baseMapper.insertRestrictionProjection(clientId, candidateId, recordId, active, expiresAt, actorId, now);
  }

  @Override
  public int projectRestriction(
      String clientId,
      String candidateId,
      String recordId,
      boolean active,
      Instant expiresAt,
      String actorId,
      Instant now) {
    return baseMapper.projectRestriction(
        clientId, candidateId, recordId, active, expiresAt, actorId, now);
  }

  @Override
  public ClientRestriction currentRestriction(String clientId, String candidateId) {
    return baseMapper.currentRestriction(clientId, candidateId);
  }

  @Override
  public int replaceRestrictionProjection(
      String clientId, String candidateId, String expectedRecordId, String recordId,
      boolean active, Instant expiresAt, String actorId, Instant now) {
    return baseMapper.replaceRestrictionProjection(
        clientId, candidateId, expectedRecordId, recordId, active, expiresAt, actorId, now);
  }

  @Override
  public int insertIdempotency(
      String actorId, String key, String action, String fingerprint, String targetId, Instant now) {
    return baseMapper.insertIdempotency(actorId, key, action, fingerprint, targetId, now);
  }
}

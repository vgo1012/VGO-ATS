package com.ezaigc.hr.recruiting.recruitment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.CollaborationRequest;
import com.ezaigc.hr.recruiting.recruitment.enums.CollaborationRequestStatus;
import com.ezaigc.hr.recruiting.recruitment.mapper.CollaborationRequestMapper;
import com.ezaigc.hr.recruiting.recruitment.service.CollaborationRequestPersistenceService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** CollaborationRequest 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CollaborationRequestPersistenceServiceImpl
    extends ServiceImpl<CollaborationRequestMapper, CollaborationRequest>
    implements CollaborationRequestPersistenceService {

  @Override
  public CollaborationRequest activeCollaboration(String lockId, String userId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(CollaborationRequest.class)
            .eq(CollaborationRequest::getLockId, lockId)
            .eq(CollaborationRequest::getRequesterUserId, userId)
            .eq(CollaborationRequest::getActiveMarker, "Y")
            .orderByDesc(CollaborationRequest::getCreateTime, CollaborationRequest::getId),
        false);
  }

  @Override
  public List<CollaborationRequest> activeCollaborations(
      Collection<String> lockIds, String userId) {
    if (lockIds == null || lockIds.isEmpty()) {
      return List.of();
    }
    return list(
        Wrappers.lambdaQuery(CollaborationRequest.class)
            .in(CollaborationRequest::getLockId, lockIds)
            .eq(CollaborationRequest::getRequesterUserId, userId)
            .eq(CollaborationRequest::getActiveMarker, "Y"));
  }

  @Override
  public List<CollaborationRequest> collaborations(String candidateId, int limit) {
    return baseMapper
        .selectPage(
            new Page<>(1, limit, false),
            Wrappers.lambdaQuery(CollaborationRequest.class)
                .eq(CollaborationRequest::getCandidateId, candidateId)
                .orderByDesc(CollaborationRequest::getCreateTime, CollaborationRequest::getId))
        .getRecords();
  }

  @Override
  public int cancelCollaborations(String lockId, String actorId, String reason, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(CollaborationRequest.class)
            .eq(CollaborationRequest::getLockId, lockId)
            .eq(CollaborationRequest::getActiveMarker, "Y")
            .set(
                CollaborationRequest::getStatus,
                CollaborationRequestStatus.CANCELLED.getCode())
            .set(CollaborationRequest::getActiveMarker, null)
            .set(CollaborationRequest::getResolvedByUserId, actorId)
            .set(CollaborationRequest::getResolutionReason, reason)
            .set(CollaborationRequest::getResolvedAt, now)
            .set(CollaborationRequest::getUpdateId, actorId)
            .set(CollaborationRequest::getUpdateTime, now));
  }
}

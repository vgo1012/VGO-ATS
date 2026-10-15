package com.ezaigc.hr.recruiting.recruitment.application.assembler;

import static com.ezaigc.hr.common.web.version.ResourceVersion.format;

import com.ezaigc.hr.recruiting.recruitment.domain.entity.CollaborationRequest;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicy;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.LockPolicyHistory;
import com.ezaigc.hr.recruiting.recruitment.domain.response.CollaborationRequestResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyHistoryResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.LockPolicyResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** 跟进归属锁定边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class EngagementLockAssembler {
  public CollaborationRequestResponse toResponse(CollaborationRequest v) {
    return new CollaborationRequestResponse(
        v.getId(),
        v.getLockId(),
        v.getCandidateId(),
        v.getRequesterUserId(),
        v.getRequesterDepartmentId(),
        v.getLockOwnerUserId(),
        v.getRequestReason(),
        v.getStatus(),
        v.getActiveMarker(),
        v.getResolvedByUserId(),
        v.getResolutionReason(),
        v.getCreateTime(),
        v.getResolvedAt());
  }

  public List<CollaborationRequestResponse> toCollaborationResponses(List<CollaborationRequest> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public LockHistoryResponse toResponse(LockHistory v) {
    return new LockHistoryResponse(
        v.getId(),
        v.getLockId(),
        v.getCandidateId(),
        v.getActionCode(),
        v.getFromOwnerUserId(),
        v.getToOwnerUserId(),
        v.getProgressType(),
        v.getReason(),
        v.getActorUserId(),
        v.getOccurredAt());
  }

  public List<LockHistoryResponse> toHistoryResponses(List<LockHistory> v) {
    return v.stream().map(this::toResponse).toList();
  }

  public LockPolicyResponse toResponse(LockPolicy v) {
    return new LockPolicyResponse(
        v.getPolicyKey(),
        v.getDurationDays(),
        format(v.getVersion()),
        v.getUpdateId(),
        v.getUpdateTime());
  }

  public LockPolicyHistoryResponse toResponse(LockPolicyHistory v) {
    return new LockPolicyHistoryResponse(
        v.getId(),
        v.getPolicyKey(),
        v.getPreviousDurationDays(),
        v.getNewDurationDays(),
        v.getReason(),
        v.getActorUserId(),
        v.getOccurredAt());
  }

  public List<LockPolicyHistoryResponse> toPolicyHistoryResponses(List<LockPolicyHistory> v) {
    return v.stream().map(this::toResponse).toList();
  }
}

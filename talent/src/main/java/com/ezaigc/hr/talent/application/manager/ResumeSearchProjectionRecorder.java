package com.ezaigc.hr.talent.application.manager;

import static com.ezaigc.hr.talent.api.ResumeFactProjectionSource.TASK_TYPE;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.infrastructure.outbox.OutboxTaskPort;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.EnqueueTask;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskPort.TaskSnapshot;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.api.enums.ResumeSearchResourceType;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 人才事实变更与搜索任务、Outbox 同事务提交，不在业务事务中调用搜索服务或 Broker。 */
@Component
public class ResumeSearchProjectionRecorder {
  @Resource private ResumeParsingPersistenceService facts;
  @Resource private OutboxTaskPort outbox;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public TaskSnapshot factChanged(String resumeVersionId, String snapshotId, String actorId) {
    var resource = facts.findResumeResource(resumeVersionId);
    if (resource == null) throw new IllegalStateException("search fact resource is no longer available");
    return enqueue(resource.getCandidateId(), "fact:" + snapshotId,
        actorId == null ? resource.getOwnerUserId() : actorId,
        resource.getOwnerUserId(), resource.getOwnerDepartmentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TaskSnapshot candidateChanged(String candidateId, String changeId, String actorId,
      String ownerUserId, String ownerDepartmentId) {
    return enqueue(candidateId, "candidate:" + changeId, actorId, ownerUserId, ownerDepartmentId);
  }

  private TaskSnapshot enqueue(String candidateId, String cause, String actorId, String ownerUserId,
      String ownerDepartmentId) {
    String key = "search:" + candidateId + ":" + cause;
    return outbox.enqueue(new EnqueueTask(ids.newId(), TASK_TYPE, ResumeSearchResourceType.CANDIDATE.getCode(), candidateId,
        actorId, ownerUserId, ownerDepartmentId, "talent.read", "talent.manage", key,
        Sha256Utils.hashUtf8(key), 5, clock.instant()));
  }
}

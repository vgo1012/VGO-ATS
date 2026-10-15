package com.ezaigc.hr.talent.application.manager;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.config.ResumeLifecycleProperties;
import com.ezaigc.hr.talent.enums.ResumeLifecycleRunStatus;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRun;
import com.ezaigc.hr.talent.domain.response.ResumeLifecycleRunResponse;
import com.ezaigc.hr.talent.domain.response.ResumeLifecycleRunsResponse;
import com.ezaigc.hr.talent.service.ResumeLifecycleRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import com.ezaigc.hr.talent.service.TalentIdempotencyPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.enums.ResumeLifecycleAction;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ResumeLifecycleAdministrationManager {
  @Resource private AuthorizationService authorization;
  @Resource private ResumeLifecycleRunPersistenceService runs;
  @Resource private ResumeLifecycleRuntimePersistenceService runtime;
  @Resource private ResumeLifecycleProperties properties;
  @Resource private TalentIdempotencyPersistenceService idempotency;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private TalentProcessEventPersistenceService audit;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;
  @Transactional(readOnly=true)
  public ResumeLifecycleRunsResponse status() {
    admin();
    var state=runtime.getById("RESUME_LIFECYCLE");
    var rows=runs.list(Wrappers.<ResumeLifecycleRun>lambdaQuery().orderByDesc(ResumeLifecycleRun::getStartedAt)
        .orderByDesc(ResumeLifecycleRun::getId).last("LIMIT 20"));
    return new ResumeLifecycleRunsResponse(properties.isEnabled(),state==null ? null : state.getLastSettlementDate(),
        state!=null && state.getCalendarGeneration()!=state.getCompletedGeneration(),
        rows.stream().map(r->new ResumeLifecycleRunResponse(r.getId(),r.getSettlementDate(),r.getStatus(),
            r.getScannedCount(),r.getSucceededCount(),r.getSharedCount(),r.getBlockedCount(),r.getFailedCount(),
            r.getErrorCode(),r.getStartedAt(),r.getFinishedAt())).toList());
  }
  @Transactional
  public ResumeLifecycleRunsResponse retry(String id,String reason,String key) {
    String actor=admin();
    var run=runs.getById(id);
    if(run==null) throw PlatformExceptions.notFound("RESUME_LIFECYCLE_RUN_NOT_FOUND","运行记录不存在");
    String fingerprint=Sha256Utils.hashUtf8(id+":"+reason);
    var replay=idempotency.findIdempotency(actor,key);
    if(replay!=null) {
      if(!ResumeLifecycleAction.RESUME_LIFECYCLE_RETRY.matches(replay.getActionCode())
          || !fingerprint.equals(replay.getRequestFingerprint())) throw PlatformExceptions.conflict("IDEMPOTENCY_KEY_REUSED","幂等键已用于其他操作");
      return status();
    }
    if(!properties.isEnabled()) throw PlatformExceptions.conflict("RESUME_LIFECYCLE_DISABLED","请先启用简历流转任务");
    if(!ResumeLifecycleRunStatus.PARTIAL.matches(run.getStatus()) && !ResumeLifecycleRunStatus.BLOCKED.matches(run.getStatus()))
      throw PlatformExceptions.conflict("RESUME_LIFECYCLE_RUN_NOT_RETRYABLE","仅部分失败或日历暂停运行可重试");
    if (intakes.insertIdempotency(actor,key,ResumeLifecycleAction.RESUME_LIFECYCLE_RETRY.getCode(),fingerprint,id,clock.instant()) != 1)
      throw PlatformExceptions.conflict("IDEMPOTENCY_CONFLICT","重试请求已提交，请刷新运行记录");
    run.setStatus(ResumeLifecycleRunStatus.RETRY_PENDING.getCode());run.setUpdateTime(clock.instant());
    if (!runs.updateById(run)) throw PlatformExceptions.versionConflict();
    audit.insertEvent(ids.newId(),ResumeLifecycleAction.RESUME_LIFECYCLE_RETRY.getCode(),actor,"RESUME_LIFECYCLE_RUN",id,reason,clock.instant());
    return status();
  }
  private String admin() {
    var actor=authorization.currentActor();
    if(!actor.isSuperAdmin()) throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED","仅管理员可管理流转任务");
    return actor.getUserId();
  }
}

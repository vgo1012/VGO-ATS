package com.ezaigc.hr.recruiting.recruitment.application.manager;
import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.activity.application.support.ActivityRequestContext;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlan;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.FollowUpPlanHistory;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.FollowUpPlanHistoryPersistenceService;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Service;
import com.ezaigc.hr.recruiting.recruitment.enums.FollowUpPlanStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
@Service
public class FollowUpPlanLifecycleManager {
  @Resource private FollowUpPlanPersistenceService plans;
  @Resource private FollowUpPlanHistoryPersistenceService histories;
  @Resource private PlatformIdGenerator ids;
  @Resource private ActivityOriginRecorder activityOrigins;
  @Transactional(propagation = Propagation.MANDATORY)
  public void audit(FollowUpPlan plan, String before, String action, String reason, String actor, Instant now) {
    var history = new FollowUpPlanHistory();
    history.setId(ids.newId()); history.setPlanId(plan.getId()); history.setAction(action);
    history.setBeforeSnapshot(before); history.setAfterSnapshot(JsonText.write(plan)); history.setReason(reason);
    history.setCreateId(actor); history.setCreateTime(now); history.setUpdateId(actor); history.setUpdateTime(now);
    if (ActivityRequestContext.isPresent()) activityOrigins.record(plan.getApplicationId(), "FOLLOW_UP_PLAN", history.getId());
    if (!histories.save(history)) throw new IllegalStateException("预约审计写入失败");
  }
  /** 调用方必须已锁定活动行；终止与改期使用同一锁顺序。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void cancelForApplication(String applicationId, String reason, String actor, Instant now) {
    for (var plan : plans.findScheduled(applicationId)) {
      String before = JsonText.write(plan);
      plan.setStatus(FollowUpPlanStatus.CANCELLED.getCode()); plan.setResultReason(reason); plan.setUpdateId(actor); plan.setUpdateTime(now);
      if (!plans.updateById(plan)) throw PlatformExceptions.conflict("FOLLOW_UP_PLAN_CHANGED", "预约已变更，请刷新");
      audit(plan, before, "APPLICATION_TERMINATED", reason, actor, now);
    }
  }
}

package com.ezaigc.hr.recruiting.activity.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.enums.ActivityAction;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ActivityOriginManager implements ActivityOriginRecorder {
  @Resource private ActivityOriginPersistenceService origins;
  @Resource private PlatformIdGenerator ids;
  @Resource private AuthorizationService authorization;
  @Resource private Clock clock;

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String applicationId, String sourceType, String sourceId) {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
    var request = attributes.getRequest();
    String action = request.getHeader("X-Recruitment-Action");
    String operation = request.getHeader("X-Recruitment-Operation");
    if (action == null && operation == null) return;
    validateContext(action, operation);
    var actor = authorization.currentActor();
    var origin = new ActivityOriginEntity();
    origin.setId(ids.newId()); origin.setApplicationId(applicationId);
    origin.setSourceType(sourceType); origin.setSourceId(sourceId);
    origin.setAction(action); origin.setOperationId(operation);
    origin.setCreateId(actor.getUserId()); origin.setUpdateId(actor.getUserId());
    origin.setCreateName(actor.getDisplayName()); origin.setUpdateName(actor.getDisplayName());
    origin.setCreateTime(clock.instant()); origin.setUpdateTime(clock.instant());
    if (!origins.save(origin)) throw new IllegalStateException("活动入口记录写入失败");
  }

  static void validateContext(String action, String operation) {
    try {
      if (ActivityAction.valueOf(action) == ActivityAction.OTHER || operation == null
          || !UUID.fromString(operation).toString().equals(operation)) throw new IllegalArgumentException();
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw PlatformExceptions.unprocessable("ACTIVITY_CONTEXT_INVALID", "活动入口及操作标识无效");
    }
  }
}

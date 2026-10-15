package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationAttempt;
import java.time.Instant;

/** NotificationAttempt 实体的持久化服务。 */
public interface NotificationAttemptPersistenceService extends IService<NotificationAttempt> {

  /** 新增通知尝试。 */
  int insertNotificationAttempt(
      String id,
      String notificationId,
      String attemptType,
      String channel,
      String status,
      String externalId,
      String errorCode,
      String errorDetail,
      String actorId,
      Instant now);
}

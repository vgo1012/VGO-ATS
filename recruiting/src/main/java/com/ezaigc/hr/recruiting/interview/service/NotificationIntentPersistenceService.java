package com.ezaigc.hr.recruiting.interview.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import java.time.Instant;
import java.util.List;

/** NotificationIntent 实体的持久化服务。 */
public interface NotificationIntentPersistenceService extends IService<NotificationIntent> {

  /** 批量新增一场面试的通知意图。 */
  boolean insertNotifications(List<NotificationIntent> notifications);

  /** 查询通知。 */
  NotificationIntent findNotification(String id);

  /** 查询通知列表。 */
  List<NotificationIntent> findNotifications(String interviewId, Instant cursorTime, String cursorId, int limit);

  /** 更新通知。 */
  int updateNotification(
      String id,
      String channel,
      String status,
      String externalId,
      String errorCode,
      String errorDetail,
      int retryIncrement,
      long version,
      String actorId,
      Instant now);
}

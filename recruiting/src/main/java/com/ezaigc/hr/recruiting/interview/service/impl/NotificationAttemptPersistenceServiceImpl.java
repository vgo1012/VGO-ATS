package com.ezaigc.hr.recruiting.interview.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationAttempt;
import com.ezaigc.hr.recruiting.interview.mapper.NotificationAttemptMapper;
import com.ezaigc.hr.recruiting.interview.service.NotificationAttemptPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** NotificationAttempt 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class NotificationAttemptPersistenceServiceImpl
    extends ServiceImpl<NotificationAttemptMapper, NotificationAttempt>
    implements NotificationAttemptPersistenceService {

  /** 新增通知尝试。 */
  @Override
  public int insertNotificationAttempt(
      String id,
      String notificationId,
      String attemptType,
      String channel,
      String status,
      String externalId,
      String errorCode,
      String errorDetail,
      String actorId,
      Instant now) {
    NotificationAttempt attempt = new NotificationAttempt();
    attempt.setId(id);
    attempt.setNotificationIntentId(notificationId);
    attempt.setAttemptType(attemptType);
    attempt.setChannel(channel);
    attempt.setStatus(status);
    attempt.setExternalMessageId(externalId);
    attempt.setErrorCode(errorCode);
    attempt.setErrorDetail(errorDetail);
    attempt.setOperatedByUserId(actorId);
    attempt.setOccurredAt(now);
    return baseMapper.insert(attempt);
  }
}

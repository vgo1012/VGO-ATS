package com.ezaigc.hr.recruiting.interview.service.impl;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTENTS_PER_INTERVIEW;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationStatus;
import com.ezaigc.hr.recruiting.interview.mapper.NotificationIntentMapper;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** NotificationIntent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class NotificationIntentPersistenceServiceImpl
    extends ServiceImpl<NotificationIntentMapper, NotificationIntent>
    implements NotificationIntentPersistenceService {

  @Override
  public boolean insertNotifications(List<NotificationIntent> notifications) {
    if (!validNotificationBatch(notifications)) {
      throw new IllegalArgumentException("interview notification batch is invalid");
    }
    return saveBatch(notifications, MAX_NOTIFICATION_INTENTS_PER_INTERVIEW);
  }

  /** 查询通知。 */
  @Override
  public NotificationIntent findNotification(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询通知列表。 */
  @Override
  public List<NotificationIntent> findNotifications(String interviewId, Instant cursorTime, String cursorId, int limit) {
    PageLimits.resolve(limit, 1, PageLimits.MAXIMUM + 1);
    var query = Wrappers.lambdaQuery(NotificationIntent.class)
            .eq(NotificationIntent::getInterviewId, interviewId)
            .and(cursorTime != null, cursor -> cursor.lt(NotificationIntent::getCreateTime, cursorTime)
                .or(tie -> tie.eq(NotificationIntent::getCreateTime, cursorTime).lt(NotificationIntent::getId, cursorId)))
            .orderByDesc(NotificationIntent::getCreateTime, NotificationIntent::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  /** 更新通知。 */
  @Override
  public int updateNotification(
      String id,
      String channel,
      String status,
      String externalId,
      String errorCode,
      String errorDetail,
      int retryIncrement,
      long version,
      String actorId,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(NotificationIntent.class)
            .set(NotificationIntent::getChannel, channel)
            .set(NotificationIntent::getStatus, status)
            .set(NotificationIntent::getExternalMessageId, externalId)
            .set(NotificationIntent::getLastErrorCode, errorCode)
            .set(NotificationIntent::getLastErrorDetail, errorDetail)
            .setIncrBy(retryIncrement != 0, NotificationIntent::getRetryCount, retryIncrement)
            // Wrapper 更新不会由 @Version 自动递增，必须显式推进版本以保持 CAS 语义。
            .setIncrBy(NotificationIntent::getVersion, 1)
            .set(NotificationIntent::getUpdateId, actorId)
            .set(NotificationIntent::getUpdateTime, now)
            .eq(NotificationIntent::getId, id)
            .eq(NotificationIntent::getVersion, version));
  }

  private static boolean validNotificationBatch(List<NotificationIntent> notifications) {
    if (notifications == null
        || notifications.isEmpty()
        || notifications.size() > MAX_NOTIFICATION_INTENTS_PER_INTERVIEW) {
      return false;
    }
    Set<String> ids = new HashSet<>();
    String interviewId =
        notifications.getFirst() == null ? null : notifications.getFirst().getInterviewId();
    for (NotificationIntent notification : notifications) {
      if (notification == null
          || CommonUtils.isBlank(notification.getId())
          || !ids.add(notification.getId())
          || CommonUtils.isBlank(notification.getInterviewId())
          || !notification.getInterviewId().equals(interviewId)
          || CommonUtils.isBlank(notification.getNotificationType())
          || CommonUtils.isBlank(notification.getRecipientType())
          || CommonUtils.isBlank(notification.getRecipientReference())
          || CommonUtils.isBlank(notification.getChannel())
          || CommonUtils.isBlank(notification.getTemplateVersionId())
          || notification.getRenderedSubject() == null
          || notification.getRenderedBody() == null
          || !InterviewNotificationStatus.PENDING_MANUAL.matches(notification.getStatus())
          || notification.getRetryCount() != 0
          || notification.getVersion() != 0L
          || CommonUtils.isBlank(notification.getCreateId())
          || notification.getCreateTime() == null
          || CommonUtils.isBlank(notification.getUpdateId())
          || notification.getUpdateTime() == null) {
        return false;
      }
    }
    return true;
  }
}

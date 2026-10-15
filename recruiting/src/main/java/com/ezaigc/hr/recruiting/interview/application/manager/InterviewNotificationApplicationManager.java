package com.ezaigc.hr.recruiting.interview.application.manager;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTENTS_PER_INTERVIEW;
import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTERVIEWS_PER_BATCH;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.domain.entity.Participant;
import com.ezaigc.hr.recruiting.interview.domain.entity.TemplateVersion;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationStatus;
import com.ezaigc.hr.recruiting.interview.enums.InterviewNotificationType;
import com.ezaigc.hr.recruiting.interview.service.InterviewPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.NotificationIntentPersistenceService;
import com.ezaigc.hr.recruiting.interview.service.ParticipantPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在已授权的面试变更事务内生成通知意图，供排期命令与招聘活动收束共用。 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class InterviewNotificationApplicationManager {
  @Resource private InterviewPersistenceService persistence;
  @Resource private ParticipantPersistenceService participantPersistenceService;
  @Resource private NotificationIntentPersistenceService notificationIntentPersistenceService;
  @Resource private PlatformIdGenerator ids;
  @Resource private Clock clock;

  /** 调用者已持有面试行锁；按有界批次当前读参与人，每场通知仍独立组成有界写入批次。 */
  public void createNotificationsBatch(List<Interview> interviews, InterviewNotificationType type, String actorId) {
    if (interviews == null || interviews.size() > MAX_NOTIFICATION_INTERVIEWS_PER_BATCH
        || interviews.stream().anyMatch(value -> value == null || value.getId() == null || value.getId().isBlank())) {
      throw new IllegalArgumentException("面试通知批次无效或超过上限");
    }
    if (interviews.isEmpty()) {
      return;
    }
    Set<String> interviewIds = interviews.stream().map(Interview::getId).collect(Collectors.toSet());
    if (interviewIds.size() != interviews.size()) {
      throw new IllegalArgumentException("面试通知批次包含重复面试");
    }
    TemplateVersion template = requireTemplate(type);
    Map<String, List<Participant>> participantsByInterview = participantPersistenceService
        .lockParticipantsByInterviewIds(interviewIds).stream()
        .collect(Collectors.groupingBy(Participant::getInterviewId));
    Instant now = clock.instant();
    for (Interview interview : interviews) {
      persistNotifications(interview, type, actorId, template,
          participantsByInterview.getOrDefault(interview.getId(), List.of()), now);
    }
  }

  private TemplateVersion requireTemplate(InterviewNotificationType type) {
    TemplateVersion template = persistence.findActiveTemplate(type.getCode());
    if (template == null) {
      throw PlatformExceptions.conflict("NOTIFICATION_TEMPLATE_NOT_ACTIVE", "通知模板未启用");
    }
    return template;
  }

  private void persistNotifications(Interview interview, InterviewNotificationType type, String actorId,
      TemplateVersion template, List<Participant> participants, Instant now) {
    String templateCode = type.getCode();
    LinkedHashSet<String> people = new LinkedHashSet<>();
    people.add(interview.getOwnerUserId());
    for (Participant participant : participants) {
      people.add(participant.getUserId());
    }
    String subject = render(template.getSubjectTemplate(), interview);
    String body = render(template.getBodyTemplate(), interview);
    List<NotificationIntent> notifications =
        new ArrayList<>(Math.min(people.size() + 1, MAX_NOTIFICATION_INTENTS_PER_INTERVIEW));
    for (String user : people) {
      notifications.add(
          notification(
              interview,
              template,
              templateCode,
              "USER",
              user,
              "IN_APP",
              subject,
              body,
              actorId,
              now));
    }
    notifications.add(
        notification(
            interview,
            template,
            templateCode,
            "CANDIDATE",
            interview.getCandidateId(),
            interview.getCandidateNotificationChannel(),
            subject,
            body,
            actorId,
            now));
    if (!notificationIntentPersistenceService.insertNotifications(List.copyOf(notifications))) {
      throw new IllegalStateException("面试通知意图批量写入失败");
    }
  }

  /** 构造待批量持久化的通知意图，不在循环中触发数据库访问。 */
  private NotificationIntent notification(
      Interview interview,
      TemplateVersion template,
      String type,
      String recipientType,
      String recipient,
      String channel,
      String subject,
      String body,
      String actorId,
      Instant now) {
    NotificationIntent notification = new NotificationIntent();
    notification.setId(ids.newId());
    notification.setInterviewId(interview.getId());
    notification.setNotificationType(type);
    notification.setRecipientType(recipientType);
    notification.setRecipientReference(recipient);
    notification.setChannel(channel);
    notification.setTemplateVersionId(template.getId());
    notification.setRenderedSubject(subject);
    notification.setRenderedBody(body);
    notification.setStatus(InterviewNotificationStatus.PENDING_MANUAL.getCode());
    notification.setRetryCount(0);
    notification.setVersion(0L);
    notification.setCreateId(actorId);
    notification.setCreateTime(now);
    notification.setUpdateId(actorId);
    notification.setUpdateTime(now);
    return notification;
  }

  private static String render(String value, Interview interview) {
    String location =
        CommonUtils.stripToNull(interview.getMeetingUrl()) != null
            ? interview.getMeetingUrl()
            : Objects.toString(interview.getLocationText(), "系统内查看");
    return value
        .replace("{{applicationId}}", interview.getApplicationId())
        .replace("{{stepCode}}", interview.getStepCode())
        .replace("{{startAt}}", interview.getScheduledStartAt().toString())
        .replace("{{timezone}}", interview.getTimezoneId())
        .replace("{{meetingMode}}", interview.getMeetingMode())
        .replace("{{location}}", location);
  }

}

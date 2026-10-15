package com.ezaigc.hr.recruiting.interview.service;

import static com.ezaigc.hr.recruiting.interview.constant.InterviewConstants.MAX_NOTIFICATION_INTENTS_PER_INTERVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.recruiting.interview.domain.entity.NotificationIntent;
import com.ezaigc.hr.recruiting.interview.service.impl.NotificationIntentPersistenceServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NotificationIntentPersistenceServiceImplTest {

  private static final String INTERVIEW_ID = "I".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void persistsNotificationIntentsThroughOneBoundedJdbcBatch() {
    NotificationIntentPersistenceServiceImpl service =
        spy(new NotificationIntentPersistenceServiceImpl());
    List<NotificationIntent> notifications =
        IntStream.range(0, MAX_NOTIFICATION_INTENTS_PER_INTERVIEW)
            .mapToObj(this::notification)
            .toList();
    doReturn(true)
        .when(service)
        .saveBatch(anyCollection(), eq(MAX_NOTIFICATION_INTENTS_PER_INTERVIEW));

    assertThat(service.insertNotifications(notifications)).isTrue();

    verify(service).saveBatch(notifications, MAX_NOTIFICATION_INTENTS_PER_INTERVIEW);
  }

  @Test
  void rejectsDuplicateAndOversizedNotificationBatchesBeforePersistence() {
    NotificationIntentPersistenceServiceImpl service =
        new NotificationIntentPersistenceServiceImpl();
    NotificationIntent duplicate = notification(1);
    List<NotificationIntent> oversized =
        IntStream.rangeClosed(0, MAX_NOTIFICATION_INTENTS_PER_INTERVIEW)
            .mapToObj(this::notification)
            .toList();

    assertThatThrownBy(() -> service.insertNotifications(List.of(duplicate, duplicate)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("interview notification batch is invalid");
    assertThatThrownBy(() -> service.insertNotifications(oversized))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("interview notification batch is invalid");
  }

  private NotificationIntent notification(int sequence) {
    NotificationIntent value = new NotificationIntent();
    value.setId(String.format("%026d", sequence));
    value.setInterviewId(INTERVIEW_ID);
    value.setNotificationType("INTERVIEW_INVITATION");
    value.setRecipientType(sequence == 0 ? "CANDIDATE" : "USER");
    value.setRecipientReference(String.format("%026d", sequence + 100));
    value.setChannel("IN_APP");
    value.setTemplateVersionId("T".repeat(26));
    value.setRenderedSubject("面试邀请");
    value.setRenderedBody("请按时参加面试");
    value.setStatus("PENDING_MANUAL");
    value.setRetryCount(0);
    value.setVersion(0L);
    value.setCreateId(ACTOR_ID);
    value.setCreateTime(NOW);
    value.setUpdateId(ACTOR_ID);
    value.setUpdateTime(NOW);
    return value;
  }
}

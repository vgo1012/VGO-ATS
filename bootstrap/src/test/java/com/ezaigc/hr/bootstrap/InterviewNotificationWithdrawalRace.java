package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.recruiting.interview.api.NotificationDeliveryPort.DeliveryResult;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 实际HTTP与MySQL保存业务事实，测试渠道只控制在途结果，不向外发送消息。 */
@RequiredArgsConstructor
final class InterviewNotificationWithdrawalRace {
  private final JdbcTemplate jdbc;
  private final ControlledNotificationDelivery delivery;

  ResultActions run(String scenario, String interviewId, String actorId,
      Callable<ResultActions> withdraw, RetryCommand retry) throws Exception {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).matches("hr_platform_it_[0-9a-f]{32}");
    String notificationId = legacyNotification(interviewId, "INTERVIEW_INVITATION", actorId);
    var original = jdbc.queryForMap("SELECT notification_type,recipient_reference,rendered_subject,rendered_body"
        + " FROM interview_notification_intent WHERE id=?", notificationId);
    boolean withdrawalFirst = scenario.equals("withdraw-before-notification");
    boolean failedDelivery = scenario.equals("notification-failure");
    String externalId = failedDelivery ? null : "synthetic-" + notificationId;
    DeliveryResult result = failedDelivery
        ? new DeliveryResult(false, null, "SYNTHETIC_REJECTED", "合成渠道拒绝")
        : new DeliveryResult(true, externalId, null, null);
    ResultActions withdrawal;
    try (var gate = delivery.control(notificationId, result)) {
      if (withdrawalFirst) {
        withdrawal = withdraw.call().andExpect(status().isOk());
        retry.execute(notificationId, "notification-send").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INTERVIEW_CANCELLED"));
        assertThat(gate.calls()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM interview_notification_intent WHERE id=?",
            String.class, notificationId)).isEqualTo("PENDING_MANUAL");
        assertThat(attemptCount(notificationId)).isZero();
      } else {
        String finalStatus = failedDelivery ? "FAILED_EXTERNAL" : "SENT_EXTERNAL";
        try (var executor = Executors.newFixedThreadPool(2)) {
          try {
            var sending = executor.submit(() -> retry.execute(notificationId, "notification-send"));
            gate.awaitEntered();
            assertThat(jdbc.queryForMap("SELECT status,retry_count,version FROM interview_notification_intent WHERE id=?",
                notificationId)).containsEntry("status", "SENDING_EXTERNAL")
                .containsEntry("retry_count", 1).containsEntry("version", 1);
            assertThat(attemptCount(notificationId)).isZero();
            // 渠道尚未返回时，撤回必须完成，不能被发送事务占用的行锁阻塞。
            withdrawal = executor.submit(withdraw).get(10, TimeUnit.SECONDS).andExpect(status().isOk());
            assertThat(sending.isDone()).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM interview_interview WHERE id=?", String.class, interviewId))
                .isEqualTo("CANCELLED");
            assertCancellationIntents(interviewId);
            gate.release();
            sending.get(10, TimeUnit.SECONDS).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(finalStatus));
          } finally {
            // 必须在等待执行器结束前释放渠道，断言失败也能回收测试线程。
            gate.release();
          }
        }
        var attempt = jdbc.queryForMap("SELECT status,external_message_id,error_code,operated_by_user_id,occurred_at"
            + " FROM interview_notification_attempt WHERE notification_intent_id=?", notificationId);
        assertThat(attempt).containsEntry("status", failedDelivery ? "FAILED" : "SENT")
            .containsEntry("external_message_id", externalId)
            .containsEntry("error_code", failedDelivery ? "SYNTHETIC_REJECTED" : null)
            .containsEntry("operated_by_user_id", actorId);
        assertThat(attempt.get("occurred_at")).isNotNull();
        retry.execute(notificationId, "notification-send").andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(finalStatus));
        assertThat(gate.calls()).isOne();
        assertThat(attemptCount(notificationId)).isOne();
      }
      retry.execute(notificationId, "notification-after-withdrawal").andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INTERVIEW_CANCELLED"));
      assertThat(gate.calls()).isEqualTo(withdrawalFirst ? 0 : 1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=?"
          + " AND idempotency_key=?", Integer.class, actorId, scenario + "-notification-after-withdrawal")).isZero();
      assertThat(jdbc.queryForMap("SELECT notification_type,recipient_reference,rendered_subject,rendered_body"
          + " FROM interview_notification_intent WHERE id=?", notificationId)).isEqualTo(original);
      assertCancellationIntents(interviewId);
      // 撤回后的取消通知仍然可发送；重放不再次调用渠道，新键不能重复发送。
      String cancelId = jdbc.queryForObject("SELECT id FROM interview_notification_intent WHERE interview_id=?"
          + " AND notification_type='INTERVIEW_CANCEL' AND recipient_type='CANDIDATE'", String.class, interviewId);
      try (var cancellation = delivery.control(cancelId, new DeliveryResult(true, "synthetic-" + cancelId, null, null))) {
        cancellation.release();
        for (int replay = 0; replay < 2; replay++) {
          retry.execute(cancelId, "notification-cancel-send").andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("SENT_EXTERNAL"));
        }
        retry.execute(cancelId, "notification-cancel-again").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NOTIFICATION_ALREADY_SENT"));
        assertThat(cancellation.calls()).isOne();
        assertThat(attemptCount(cancelId)).isOne();
      }
    }
    return withdrawal;
  }

  private String legacyNotification(String interviewId, String type, String actorId) {
    // 新预约不自动创建邀请；显式历史邀请仍须保留发送、重放及撤回并发语义。
    String id = UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    assertThat(jdbc.update("""
        INSERT INTO interview_notification_intent(id,interview_id,notification_type,recipient_type,
          recipient_reference,channel,template_version_id,rendered_subject,rendered_body,status,create_id)
        SELECT ?,?,?,'CANDIDATE','historical-candidate@example.com','EMAIL',v.id,
          '历史面试通知','升级前已创建的面试通知事实','PENDING_MANUAL',?
        FROM interview_template t JOIN interview_template_version v ON v.template_id=t.id
          AND v.version_no=t.current_version_no WHERE t.code=?
        """, id, interviewId, type, actorId, type)).isOne();
    return id;
  }

  private int attemptCount(String notificationId) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM interview_notification_attempt WHERE notification_intent_id=?",
        Integer.class, notificationId);
  }

  private void assertCancellationIntents(String interviewId) {
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_notification_intent WHERE interview_id=?"
        + " AND notification_type='INTERVIEW_CANCEL'", Integer.class, interviewId)).isEqualTo(2);
  }

  @FunctionalInterface
  interface RetryCommand {
    ResultActions execute(String notificationId, String key) throws Exception;
  }
}

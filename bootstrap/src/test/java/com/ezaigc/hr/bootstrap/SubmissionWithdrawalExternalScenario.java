package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 复用实库验收建立的合成候选人/JD；所有业务动作经过真实HTTP与事务代理。 */
@RequiredArgsConstructor
final class SubmissionWithdrawalExternalScenario {
  private final MockMvc mvc;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Cookie[] session;
  private final JsonNode csrf;
  private final String scenario;
  private final ControlledNotificationDelivery notificationDelivery;

  void verify(String candidateId, String jdVersionId, String resumeVersionId, String actorId)
      throws Exception {
    // 故障触发器只能建立在本次隔离空库迁移得到的数据库中。
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
        .matches("hr_platform_it_[0-9a-f]{32}");
    JsonNode created = read(write("/api/v1/applications", "withdraw-application", Map.of(
        "candidateId", candidateId, "jdVersionId", jdVersionId,
        "resumeVersionId", resumeVersionId, "reason", "实库撤回联动验收"))
        .andExpect(status().isCreated()));
    String appId = created.get("application").get("id").asString();
    String appUrl = "/api/v1/applications/" + appId;
    String interviewStepId = step(created, "INTERVIEW_ROUND_1").get("id").asString();
    JsonNode selected = read(write(appUrl + "/next-actions", "withdraw-path", Map.of(
        "stepCode", "CLIENT_SUBMISSION", "version", 0, "reason", "推荐后采用甲方面试"))
        .andExpect(status().isOk()));
    JsonNode submissionStep = step(selected, "CLIENT_SUBMISSION");
    String submissionStepId = submissionStep.get("id").asString();
    String submissionStepUrl = appUrl + "/workflow-steps/" + submissionStepId;
    JsonNode draft = read(write("/api/v1/client-submissions", "withdraw-draft",
        Map.of("applicationId", appId)).andExpect(status().isCreated()));
    String submissionId = draft.get("submission").get("id").asString();
    String submissionUrl = "/api/v1/client-submissions/" + submissionId;
    JsonNode sent = read(write(submissionUrl + "/send", "withdraw-send", Map.of(
        "recipient", "withdraw-client@example.com", "channel", "EMAIL",
        "reason", "客户确认接收", "version", draft.get("submission").get("version").asLong()))
        .andExpect(status().isOk()));
    JsonNode sendSnapshot = sent.get("sendSnapshot");
    write(submissionStepUrl + "/follow-ups", "withdraw-follow-up", Map.of(
        "channel", "CLIENT_SYSTEM", "summary", "客户进入面试环节", "nextPlan", "安排甲方面试"))
        .andExpect(status().isCreated());
    submissionStep = step(readGet(appUrl), "CLIENT_SUBMISSION");
    JsonNode completed = read(write(submissionStepUrl + "/complete", "withdraw-submission-complete", Map.of(
        "result", "PASSED", "reason", "客户已接收推荐", "clientSubmissionId", submissionId,
        "version", submissionStep.get("version").asLong()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.application.currentStage").value("PATH_SELECTION")));
    assertThat(step(completed, "CLIENT_SUBMISSION").get("status").asString()).isEqualTo("PASSED");
    write(appUrl + "/next-actions", "withdraw-interview-start", Map.of(
        "stepCode", "INTERVIEW_ROUND_1", "reason", "客户进入首轮面试",
        "version", completed.get("application").get("version").asLong()))
        .andExpect(status().isOk());
    Instant start = Instant.now().plusSeconds(14400);
    Map<String, Object> interviewCommand = Map.ofEntries(
        Map.entry("applicationId", appId), Map.entry("workflowStepId", interviewStepId),
        Map.entry("startAt", start.toString()), Map.entry("endAt", start.plusSeconds(3600).toString()),
        Map.entry("timezoneId", "Asia/Shanghai"), Map.entry("meetingMode", "ONLINE"),
        Map.entry("meetingUrl", "https://meeting.example/withdraw-it"), Map.entry("participantUserIds", List.of(actorId)),
        Map.entry("clientSubmissionId", submissionId),
        Map.entry("reason", "建立待撤回甲方面试"));
    if (scenario.equals("completed-page")) {
      // 通过真实排期/正式面评建立历史，不能用不参与查询的CANCELLED行凑分页数量。
      for (int number = 0; number < 100; number++) {
        String historicalId = read(write("/api/v1/interviews", "completed-interview-" + number, interviewCommand)
            .andExpect(status().isCreated())).get("interview").get("id").asString();
        write("/api/v1/interviews/" + historicalId + "/feedback", "completed-feedback-" + number, Map.of(
            "verdict", "PASS", "shortComment", "已完成甲方面试 " + number,
            "externalFeedbackSource", "甲方负责人电话确认", "status", "SUBMITTED", "changeReason", "正式记录实际反馈"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback.status").value("SUBMITTED"));
      }
    }
    List<Map<String, Object>> completedFacts = completedInterviewFacts(appId);
    if (scenario.equals("completed-page")) assertThat(completedFacts).hasSize(100);
    JsonNode scheduled = read(write("/api/v1/interviews", "withdraw-interview", interviewCommand)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.interview.status").value("SCHEDULED")));
    String interviewId = scheduled.get("interview").get("id").asString();
    Map<String, Object> submittedCommand = Map.of("verdict", "PASS", "shortComment", "撤回前已正式提交的面评",
        "externalFeedbackSource", "甲方电话", "status", "SUBMITTED", "changeReason", "正式提交已有反馈");
    JsonNode savedFeedback = scenario.equals("feedback-replay")
        ? read(write("/api/v1/interviews/" + interviewId + "/feedback", "draft-before-withdrawal", submittedCommand)
            .andExpect(status().isOk())) : null;
    if (scenario.equals("feedback-limits")) verifyFeedbackLengths(interviewId);
    if (scenario.equals("feedback-replay") || scenario.equals("feedback-limits")) completedFacts = completedInterviewFacts(appId);
    if (scenario.equals("completed-page")) {
      assertThat(jdbc.queryForList("""
          SELECT id FROM interview_interview WHERE application_id=? AND client_submission_id=?
          AND status IN ('SCHEDULED','COMPLETED') ORDER BY id LIMIT 100
          """, String.class, appId, submissionId)).hasSize(100).doesNotContain(interviewId);
    }
    if (scenario.equals("legacy")) {
      // 仅构造历史不完整关联：面试仍绑定推荐，而步骤上的绑定为空。
      assertThat(jdbc.update("UPDATE recruitment_application_workflow_step SET client_submission_id=NULL WHERE id=? AND client_submission_id=?",
          interviewStepId, submissionId)).isOne();
    }
    mvc.perform(get("/api/v1/workbench").cookie(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.stepId=='%s')]".formatted(interviewStepId)).isNotEmpty());

    JsonNode beforeSubmission = readGet(submissionUrl).get("submission");
    Map<String, Object> command = Map.of("reason", "客户取消本轮面试",
        "version", beforeSubmission.get("version").asLong());
    Map<String, Object> before = snapshot(appId, submissionId, interviewId);
    // 阶段历史是撤回链的最后写入点；在真实MySQL中使其失败，观察先前更新是否回滚。
    jdbc.execute("""
        CREATE TRIGGER fail_withdrawal_stage_history BEFORE INSERT ON recruitment_stage_history
        FOR EACH ROW BEGIN
          IF NEW.to_stage = 'PATH_SELECTION' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Synthetic withdrawal history failure';
          END IF;
        END
        """);
    try {
      write(submissionUrl + "/withdraw", "withdraw-command", command)
          .andExpect(status().isInternalServerError());
      assertThat(snapshot(appId, submissionId, interviewId)).isEqualTo(before);
      mvc.perform(get("/api/v1/workbench").cookie(session)).andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.stepId=='%s')]".formatted(interviewStepId)).isNotEmpty());
    } finally {
      jdbc.execute("DROP TRIGGER fail_withdrawal_stage_history");
    }

    ResultActions withdrawal;
    String checkedInterviewId = interviewId;
    if (scenario.equals("feedback-first") || scenario.equals("withdraw-before-feedback")) {
      withdrawal = verifyFeedbackRace(candidateId, appId, interviewId, actorId, submissionUrl, command);
    } else if (scenario.equals("details-update-first") || scenario.equals("withdraw-before-details-update")) {
      withdrawal = verifyDetailsRace(appId, submissionId, interviewId, actorId, submissionUrl, command, start,
          scheduled.get("interview").get("version").asLong());
    } else if (scenario.equals("cancel-first") || scenario.equals("withdraw-before-cancel")) {
      boolean withdrawalFirst = scenario.equals("withdraw-before-cancel");
      String cancelUrl = "/api/v1/interviews/" + interviewId + "/cancel";
      Map<String, Object> cancelCommand = Map.of(
          "version", scheduled.get("interview").get("version").asLong(), "reason", "与推荐撤回同时取消面试");
      var race = new InterviewWithdrawalExternalRace(jdbc).runCancellation(withdrawalFirst,
          () -> write(submissionUrl + "/withdraw", "withdraw-command", command),
          () -> write(cancelUrl, "racing-cancel", cancelCommand));
      withdrawal = race.withdrawal();
      assertThat(jdbc.queryForObject("SELECT version FROM interview_interview WHERE id=?", Long.class, interviewId))
          .isEqualTo(scheduled.get("interview").get("version").asLong() + 1);
      var afterRace = snapshot(appId, submissionId, interviewId);
      ResultActions retry = write(cancelUrl, "racing-cancel", cancelCommand);
      if (withdrawalFirst) {
        retry.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTERVIEW_NOT_SCHEDULED"));
      } else {
        retry.andExpect(status().isOk()).andExpect(jsonPath("$.interview.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("SELECT reason FROM interview_schedule_history WHERE interview_id=? AND action_type='CANCEL'",
            String.class, interviewId)).isEqualTo("与推荐撤回同时取消面试");
      }
      assertThat(snapshot(appId, submissionId, interviewId)).isEqualTo(afterRace);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=? AND idempotency_key=?",
          Integer.class, actorId, scenario + "-racing-cancel")).isEqualTo(withdrawalFirst ? 0 : 1);
    } else if (scenario.equals("create-first") || scenario.equals("withdraw-before-create")) {
      boolean withdrawalFirst = scenario.equals("withdraw-before-create");
      write("/api/v1/interviews/" + interviewId + "/cancel", "cancel-old-schedule", Map.of(
          "version", scheduled.get("interview").get("version").asLong(), "reason", "取消旧排期后验证重新安排与撤回竞争"))
          .andExpect(status().isOk());
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_interview WHERE workflow_step_id=? AND status='SCHEDULED'",
          Integer.class, interviewStepId)).isZero();
      var race = new InterviewWithdrawalExternalRace(jdbc).runCreation(withdrawalFirst,
          () -> write(submissionUrl + "/withdraw", "withdraw-command", command),
          () -> write("/api/v1/interviews", "racing-create", interviewCommand));
      withdrawal = race.withdrawal();
      if (!withdrawalFirst) {
        checkedInterviewId = read(race.competing()).get("interview").get("id").asString();
        assertThat(checkedInterviewId).isNotEqualTo(interviewId);
      }
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_interview WHERE application_id=?",
          Integer.class, appId)).isEqualTo(withdrawalFirst ? 1 : 2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=? AND idempotency_key=?",
          Integer.class, actorId, scenario + "-racing-create")).isEqualTo(withdrawalFirst ? 0 : 1);
    } else if (scenario.equals("withdraw-first") || scenario.equals("reschedule-first")
        || scenario.equals("reschedule-update-first") || scenario.equals("withdraw-before-reschedule-update")) {
      boolean withdrawalFirst = scenario.equals("withdraw-first") || scenario.equals("withdraw-before-reschedule-update");
      boolean beforeScheduleUpdate = scenario.equals("reschedule-update-first") || scenario.equals("withdraw-before-reschedule-update");
      withdrawal = new InterviewWithdrawalExternalRace(jdbc).run(withdrawalFirst, beforeScheduleUpdate,
          () -> write(submissionUrl + "/withdraw", "withdraw-command", command),
          () -> write("/api/v1/interviews/" + interviewId + "/reschedule", "racing-reschedule", Map.of(
              "startAt", start.plusSeconds(7200).toString(), "endAt", start.plusSeconds(10800).toString(),
              "timezoneId", "Asia/Shanghai", "version", scheduled.get("interview").get("version").asLong(),
              "reason", "与推荐撤回同时改期")));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_schedule_history WHERE interview_id=? AND action_type='RESCHEDULE'",
          Integer.class, interviewId)).isEqualTo(withdrawalFirst ? 0 : 1);
    } else if (scenario.equals("notification-first") || scenario.equals("notification-failure")
        || scenario.equals("withdraw-before-notification")) {
      withdrawal = new InterviewNotificationWithdrawalRace(jdbc, notificationDelivery).run(scenario, interviewId, actorId,
          () -> write(submissionUrl + "/withdraw", "withdraw-command", command),
          (notificationId, key) -> write("/api/v1/notifications/" + notificationId + "/retry", key, Map.of()));
    } else {
      withdrawal = write(submissionUrl + "/withdraw", "withdraw-command", command);
    }
    JsonNode withdrawn = read(withdrawal
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.submission.deliveryStatus").value("WITHDRAWN"))
        .andExpect(jsonPath("$.submission.withdrawalReason").value("客户取消本轮面试"))
        .andExpect(jsonPath("$.submission.withdrawnByUserId").value(actorId)));
    assertThat(withdrawn.get("sendSnapshot")).isEqualTo(sendSnapshot);
    JsonNode afterApplication = readGet(appUrl);
    assertThat(afterApplication.get("application").get("currentStage").asString()).isEqualTo("PATH_SELECTION");
    assertThat(step(afterApplication, "CLIENT_SUBMISSION").get("status").asString()).isEqualTo("PASSED");
    assertThat(step(afterApplication, "INTERVIEW_ROUND_1").get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(step(afterApplication, "INTERVIEW_ROUND_1").get("clientSubmissionId").asString()).isEqualTo(submissionId);
    assertThat(readGet("/api/v1/interviews/" + checkedInterviewId).get("interview").get("status").asString())
        .isEqualTo(completedBeforeWithdrawal() ? "COMPLETED" : "CANCELLED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_schedule_history WHERE interview_id=? AND action_type='CANCEL'",
        Integer.class, checkedInterviewId)).isEqualTo(completedBeforeWithdrawal() ? 0 : 1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_notification_intent WHERE interview_id=? AND notification_type='INTERVIEW_CANCEL'",
        Integer.class, checkedInterviewId)).as("%s 仅撤回本次取消的排期产生通知", scenario)
        .isEqualTo(completedBeforeWithdrawal() || scenario.equals("cancel-first")
            || scenario.equals("withdraw-before-create") ? 0 : scenario.equals("details-update-first") ? 3 : 2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_interview WHERE application_id=? AND status='SCHEDULED'",
        Integer.class, appId)).isZero();
    if (scenario.equals("feedback-first")) {
      assertThat(completedFacts).isEmpty();
      assertThat(completedInterviewFacts(appId)).hasSize(1);
    } else {
      assertThat(completedInterviewFacts(appId)).isEqualTo(completedFacts);
    }
    if (savedFeedback != null) {
      String feedbackUrl = "/api/v1/interviews/" + interviewId + "/feedback";
      var feedbackBeforeReplay = feedbackFacts(interviewId);
      long reservations = jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=?",
          Long.class, actorId);
      assertThat(read(write(feedbackUrl, "draft-before-withdrawal", submittedCommand).andExpect(status().isOk())))
          .isEqualTo(savedFeedback);
      write(feedbackUrl, "draft-after-withdrawal", submittedCommand).andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INTERVIEW_COMPLETED"));
      write(feedbackUrl, "draft-before-withdrawal", Map.of("verdict", "FAIL", "shortComment", "修改旧请求",
          "externalFeedbackSource", "甲方电话", "status", "SUBMITTED", "changeReason", "正式提交已有反馈"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
      assertThat(feedbackFacts(interviewId)).isEqualTo(feedbackBeforeReplay);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=?",
          Long.class, actorId)).isEqualTo(reservations);
    }
    if (scenario.equals("completed-page")) {
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM interview_notification_intent n
          JOIN interview_interview i ON i.id=n.interview_id
          WHERE i.application_id=? AND n.notification_type='INTERVIEW_CANCEL'
          """, Integer.class, appId)).isEqualTo(2);
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_decision_record WHERE application_id=?",
        Integer.class, appId)).isZero();
    mvc.perform(get("/api/v1/workbench").cookie(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.applicationId=='%s')]".formatted(appId)).isEmpty());
    Map<String, Object> committed = snapshot(appId, submissionId, checkedInterviewId);
    write(submissionUrl + "/withdraw", "withdraw-command", command).andExpect(status().isOk());
    assertThat(snapshot(appId, submissionId, checkedInterviewId)).isEqualTo(committed);
    write(submissionUrl + "/withdraw", "withdraw-new-command", command)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBMISSION_WITHDRAWAL_NOT_ALLOWED"));
    assertThat(snapshot(appId, submissionId, checkedInterviewId)).isEqualTo(committed);

    // HR明确重新选路后才出现人工决定待办；旧推荐仍不能作为通过依据。
    write(appUrl + "/next-actions", "withdraw-reselect", Map.of(
        "stepCode", "DECISION", "reason", "撤回后改由HR作出人工决定",
        "version", afterApplication.get("application").get("version").asLong()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.application.currentStage").value("DECISION"));
    write(appUrl + "/decisions", "withdraw-old-submission-pass", Map.of(
        "decision", "PASS", "reason", "验证已撤回推荐不可复用", "clientSubmissionId", submissionId))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("CLIENT_SUBMISSION_MISMATCH"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_decision_record WHERE application_id=?",
        Integer.class, appId)).isZero();
    mvc.perform(get("/api/v1/workbench").cookie(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.applicationId=='%s' && @.stepCode=='DECISION')]".formatted(appId)).isNotEmpty());
    // 显式人工结束此场景，后续场景可为同一合成候选人与JD建立新的活动。
    write(appUrl + "/decisions", "finish-scenario", Map.of(
        "decision", "WITHDRAW", "reason", "HR确认结束本轮实库验收流程"))
        .andExpect(status().isCreated());
  }

  private ResultActions verifyDetailsRace(String appId, String submissionId, String interviewId, String actorId,
      String submissionUrl, Map<String, Object> withdrawalCommand, Instant start, long version) throws Exception {
    boolean withdrawalFirst = scenario.equals("withdraw-before-details-update");
    String otherUser = jdbc.queryForObject("SELECT id FROM identity_user_account WHERE normalized_email=? AND status='ACTIVE'",
        String.class, "recruiter-it@example.com");
    assertThat(otherUser).isNotBlank().isNotEqualTo(actorId);
    String endpoint = "/api/v1/interviews/" + interviewId;
    Map<String, Object> updateCommand = Map.ofEntries(
        Map.entry("startAt", start.plusSeconds(7200).toString()),
        Map.entry("endAt", start.plusSeconds(10800).toString()),
        Map.entry("timezoneId", "Asia/Tokyo"), Map.entry("meetingMode", "ONSITE"),
        Map.entry("locationText", "合成更新会议室"), Map.entry("meetingUrl", ""),
        Map.entry("participantUserIds", List.of(otherUser)), Map.entry("candidateNotificationChannel", "EMAIL"),
        Map.entry("nextResponsibleUserId", otherUser), Map.entry("version", Long.toString(version)),
        Map.entry("reason", "与推荐撤回同时修改面试详情"));
    String detailsSql = "SELECT scheduled_start_at,scheduled_end_at,timezone_id,meeting_mode,location_text,"
        + "meeting_url,candidate_notification_channel,next_responsible_user_id FROM interview_interview WHERE id=?";
    var beforeDetails = jdbc.queryForMap(detailsSql, interviewId);
    var beforeTimes = jdbc.queryForObject("SELECT scheduled_start_at,scheduled_end_at FROM interview_interview WHERE id=?",
        (row, number) -> List.of(row.getTimestamp(1).toInstant(), row.getTimestamp(2).toInstant()), interviewId);
    var beforeParticipants = jdbc.queryForList("SELECT * FROM interview_participant WHERE interview_id=? ORDER BY user_id", interviewId);
    // 在实际 UPDATE 之前持有面试行锁，让另一请求真正进入锁等待，再继续替换参与人和追加历史。
    ResultActions withdrawal = new InterviewWithdrawalExternalRace(jdbc).run(withdrawalFirst, true,
        () -> write(submissionUrl + "/withdraw", "withdraw-command", withdrawalCommand),
        () -> update(endpoint, "racing-details", updateCommand));
    assertThat(jdbc.queryForObject("SELECT version FROM interview_interview WHERE id=?", Long.class, interviewId))
        .isEqualTo(version + (withdrawalFirst ? 1 : 2));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_schedule_history WHERE interview_id=? AND action_type='UPDATE'",
        Integer.class, interviewId)).isEqualTo(withdrawalFirst ? 0 : 1);
    if (withdrawalFirst) {
      assertThat(jdbc.queryForMap(detailsSql, interviewId)).isEqualTo(beforeDetails);
      assertThat(jdbc.queryForList("SELECT * FROM interview_participant WHERE interview_id=? ORDER BY user_id", interviewId))
          .isEqualTo(beforeParticipants);
    } else {
      assertThat(jdbc.queryForMap(detailsSql, interviewId)).containsEntry("timezone_id", "Asia/Tokyo")
          .containsEntry("meeting_mode", "ONSITE").containsEntry("location_text", "合成更新会议室")
          .containsEntry("meeting_url", null).containsEntry("candidate_notification_channel", "EMAIL")
          .containsEntry("next_responsible_user_id", otherUser);
      assertThat(jdbc.queryForObject("SELECT scheduled_start_at FROM interview_interview WHERE id=?", Timestamp.class, interviewId).toInstant())
          .isEqualTo(beforeTimes.get(0).plusSeconds(7200));
      assertThat(jdbc.queryForObject("SELECT scheduled_end_at FROM interview_interview WHERE id=?", Timestamp.class, interviewId).toInstant())
          .isEqualTo(beforeTimes.get(1).plusSeconds(7200));
      assertThat(jdbc.queryForList("SELECT user_id FROM interview_participant WHERE interview_id=?", String.class, interviewId))
          .containsExactly(otherUser);
      assertThat(jdbc.queryForObject("SELECT reason FROM interview_schedule_history WHERE interview_id=? AND action_type='UPDATE'",
          String.class, interviewId)).isEqualTo("与推荐撤回同时修改面试详情");
    }
    assertThat(jdbc.queryForList("SELECT recipient_reference FROM interview_notification_intent WHERE interview_id=?"
        + " AND notification_type='INTERVIEW_CANCEL' AND recipient_type='USER' ORDER BY recipient_reference", String.class, interviewId))
        .containsExactlyInAnyOrderElementsOf(withdrawalFirst ? List.of(actorId) : List.of(actorId, otherUser));
    assertThat(jdbc.queryForObject("SELECT channel FROM interview_notification_intent WHERE interview_id=?"
        + " AND notification_type='INTERVIEW_CANCEL' AND recipient_type='CANDIDATE'", String.class, interviewId))
        .isEqualTo(withdrawalFirst ? "NONE" : "EMAIL");
    var committedDetails = jdbc.queryForMap(detailsSql, interviewId);
    var committedParticipants = jdbc.queryForList("SELECT * FROM interview_participant WHERE interview_id=? ORDER BY user_id", interviewId);
    var committed = snapshot(appId, submissionId, interviewId);
    ResultActions replay = update(endpoint, "racing-details", updateCommand);
    if (withdrawalFirst) {
      replay.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INTERVIEW_NOT_SCHEDULED"));
    } else {
      replay.andExpect(status().isOk()).andExpect(jsonPath("$.interview.status").value("CANCELLED"));
    }
    assertThat(jdbc.queryForMap(detailsSql, interviewId)).isEqualTo(committedDetails);
    assertThat(jdbc.queryForList("SELECT * FROM interview_participant WHERE interview_id=? ORDER BY user_id", interviewId))
        .isEqualTo(committedParticipants);
    assertThat(snapshot(appId, submissionId, interviewId)).isEqualTo(committed);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=? AND idempotency_key=?",
        Integer.class, actorId, scenario + "-racing-details")).isEqualTo(withdrawalFirst ? 0 : 1);
    return withdrawal;
  }

  private Map<String, Object> snapshot(String appId, String submissionId, String interviewId) {
    return Map.of(
        "application", jdbc.queryForMap("SELECT current_stage,outcome,version FROM recruitment_application WHERE id=?", appId),
        "submission", jdbc.queryForMap("SELECT delivery_status,process_status,version,withdrawn_at,withdrawal_reason FROM submission_client_submission WHERE id=?", submissionId),
        "steps", jdbc.queryForList("SELECT id,status,client_submission_id,version FROM recruitment_application_workflow_step WHERE application_id=? ORDER BY id", appId),
        "interview", jdbc.queryForMap("SELECT status,version FROM interview_interview WHERE id=?", interviewId),
        "completedFacts", completedInterviewFacts(appId),
        "notifications", jdbc.queryForList("SELECT id,notification_type,status,version FROM interview_notification_intent WHERE interview_id=? ORDER BY id", interviewId),
        "scheduleHistory", jdbc.queryForObject("SELECT COUNT(*) FROM interview_schedule_history WHERE interview_id=?", Integer.class, interviewId),
        "stageHistory", jdbc.queryForObject("SELECT COUNT(*) FROM recruitment_stage_history WHERE application_id=?", Integer.class, appId),
        "idempotency", jdbc.queryForObject("SELECT COUNT(*) FROM submission_idempotency_record WHERE target_id=?", Integer.class, submissionId));
  }

  private ResultActions verifyFeedbackRace(String candidateId, String appId, String interviewId,
      String actorId, String submissionUrl, Map<String, Object> withdrawalCommand) throws Exception {
    boolean withdrawalFirst = scenario.equals("withdraw-before-feedback");
    String lockId = read(write("/api/v1/candidates/" + candidateId + "/engagement-locks", "race-lock",
        Map.of("reason", "候选人确认继续跟进，验证并发面评的真实进展"))
        .andExpect(status().isCreated())).get("id").asString();
    var lockBefore = jdbc.queryForMap("SELECT * FROM recruitment_candidate_engagement_lock WHERE id=?", lockId);
    var progressBefore = jdbc.queryForList(
        "SELECT * FROM recruitment_candidate_engagement_lock_history WHERE lock_id=? ORDER BY id", lockId);
    int interviewVersion = jdbc.queryForObject("SELECT version FROM interview_interview WHERE id=?", Integer.class, interviewId);
    String feedbackUrl = "/api/v1/interviews/" + interviewId + "/feedback";
    Map<String, Object> feedbackCommand = Map.of("verdict", "PASS", "shortComment", "正式反馈与推荐撤回同时到达",
        "externalFeedbackSource", "甲方负责人电话确认", "status", "SUBMITTED", "changeReason", "记录实际面试结果");
    var race = new InterviewWithdrawalExternalRace(jdbc).runFeedback(withdrawalFirst,
        () -> write(submissionUrl + "/withdraw", "withdraw-command", withdrawalCommand),
        () -> write(feedbackUrl, "racing-feedback", feedbackCommand));
    assertThat(jdbc.queryForMap("SELECT status,version,update_id FROM interview_interview WHERE id=?", interviewId))
        .containsEntry("status", withdrawalFirst ? "CANCELLED" : "COMPLETED")
        .containsEntry("version", interviewVersion + 1).containsEntry("update_id", actorId);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM interview_idempotency_record WHERE actor_user_id=? AND idempotency_key=?",
        Integer.class, actorId, scenario + "-racing-feedback")).isEqualTo(withdrawalFirst ? 0 : 1);
    var lockAfter = jdbc.queryForMap("SELECT * FROM recruitment_candidate_engagement_lock WHERE id=?", lockId);
    var progressAfter = jdbc.queryForList(
        "SELECT * FROM recruitment_candidate_engagement_lock_history WHERE lock_id=? ORDER BY id", lockId);
    if (withdrawalFirst) {
      assertThat(feedbackFacts(interviewId)).isEqualTo(Map.of("feedback", List.of(), "versions", List.of()));
      assertThat(lockAfter).isEqualTo(lockBefore);
      assertThat(progressAfter).isEqualTo(progressBefore);
    } else {
      JsonNode saved = read(race.competing());
      var facts = completedInterviewFacts(appId);
      assertThat(facts).hasSize(1);
      assertThat(facts.getFirst()).containsEntry("id", interviewId)
          .containsEntry("status", "COMPLETED")
          .containsEntry("feedback_id", saved.get("feedback").get("id").asString())
          .containsEntry("feedback_status", "SUBMITTED")
          .containsEntry("current_version_no", 1)
          .containsEntry("feedback_version_id", saved.get("currentVersion").get("id").asString())
          .containsEntry("version_no", 1).containsEntry("verdict", "PASS")
          .containsEntry("short_comment", feedbackCommand.get("shortComment"))
          .containsEntry("external_feedback_source", feedbackCommand.get("externalFeedbackSource"));
      assertThat(lockAfter).containsEntry("last_progress_type", "INTERVIEW_COMPLETED")
          .containsEntry("version", ((Number) lockBefore.get("version")).intValue() + 1);
      assertThat(progressAfter).hasSize(progressBefore.size() + 1);
      assertThat(progressAfter.subList(0, progressBefore.size())).isEqualTo(progressBefore);
      assertThat(progressAfter.getLast()).containsEntry("progress_type", "INTERVIEW_COMPLETED")
          .containsEntry("actor_user_id", actorId);
      // 两个事务均提交后，历史结果与重放仍精确相同，不能追加面评版本或续期。
      JsonNode feedbackHistory = readGet(feedbackUrl).get("items");
      assertThat(feedbackHistory.size()).isOne();
      assertThat(feedbackHistory.get(0)).isEqualTo(saved);
      var feedbackBeforeReplay = feedbackFacts(interviewId);
      assertThat(read(write(feedbackUrl, "racing-feedback", feedbackCommand).andExpect(status().isOk()))).isEqualTo(saved);
      assertThat(feedbackFacts(interviewId)).isEqualTo(feedbackBeforeReplay);
      assertThat(completedInterviewFacts(appId)).isEqualTo(facts);
      assertThat(jdbc.queryForMap("SELECT * FROM recruitment_candidate_engagement_lock WHERE id=?", lockId)).isEqualTo(lockAfter);
      assertThat(jdbc.queryForList("SELECT * FROM recruitment_candidate_engagement_lock_history WHERE lock_id=? ORDER BY id", lockId))
          .isEqualTo(progressAfter);
    }
    write("/api/v1/engagement-locks/" + lockId + "/release", "release-race-lock", Map.of("reason", "结束并发场景验收"))
        .andExpect(status().isOk());
    return race.withdrawal();
  }

  private List<Map<String, Object>> completedInterviewFacts(String appId) {
    return jdbc.queryForList("""
        SELECT i.id,i.status,i.version,f.id feedback_id,f.status feedback_status,f.current_version_no,
               v.id feedback_version_id,v.version_no,v.verdict,v.short_comment,
               v.external_feedback_source,v.feedback_occurred_at
        FROM interview_interview i
        JOIN interview_feedback f ON f.interview_id=i.id
        JOIN interview_feedback_version v ON v.feedback_id=f.id
        WHERE i.application_id=? AND i.status='COMPLETED'
        ORDER BY i.id,v.version_no
        """, appId);
  }

  private Map<String, Object> feedbackFacts(String interviewId) {
    return Map.of(
        "feedback", jdbc.queryForList("SELECT * FROM interview_feedback WHERE interview_id=? ORDER BY id", interviewId),
        "versions", jdbc.queryForList("SELECT v.* FROM interview_feedback_version v JOIN interview_feedback f ON f.id=v.feedback_id"
            + " WHERE f.interview_id=? ORDER BY v.id", interviewId));
  }

  private void verifyFeedbackLengths(String interviewId) throws Exception {
    String endpoint = "/api/v1/interviews/" + interviewId + "/feedback";
    String comment = "评".repeat(1000);
    String source = "源".repeat(500);
    write(endpoint, "fields-at-limit", Map.of(
        "verdict", "PASS", "shortComment", comment, "externalFeedbackSource", source,
        "status", "SUBMITTED", "changeReason", "验证正式面评字段长度边界"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion.shortComment").value(comment))
        .andExpect(jsonPath("$.currentVersion.externalFeedbackSource").value(source));
    write(endpoint, "comment-over-limit", Map.of(
        "verdict", "PASS", "shortComment", comment + "评", "externalFeedbackSource", "甲方电话",
        "status", "SUBMITTED", "changeReason", "拒绝超长意见"))
        .andExpect(status().isBadRequest());
    write(endpoint, "source-over-limit", Map.of(
        "verdict", "PASS", "shortComment", "简要意见", "externalFeedbackSource", source + "源",
        "status", "SUBMITTED", "changeReason", "拒绝超长来源"))
        .andExpect(status().isBadRequest());
    var versions = jdbc.queryForList("""
        SELECT v.version_no,v.short_comment,v.external_feedback_source
        FROM interview_feedback_version v JOIN interview_feedback f ON f.id=v.feedback_id
        WHERE f.interview_id=? ORDER BY v.version_no
        """, interviewId);
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0)).containsEntry("short_comment", comment);
    assertThat(versions.get(0)).containsEntry("external_feedback_source", source);
  }

  private boolean completedBeforeWithdrawal() {
    return scenario.equals("feedback-first") || scenario.equals("feedback-replay") || scenario.equals("feedback-limits");
  }

  private static JsonNode step(JsonNode details, String code) {
    for (JsonNode step : details.get("workflowSteps")) {
      if (code.equals(step.get("stepCode").asString())) return step;
    }
    throw new AssertionError("缺少工作流步骤 " + code);
  }

  private ResultActions write(String url, String key, Object body) throws Exception {
    return mvc.perform(post(url).cookie(session)
        .header(csrf.get("headerName").asString(), csrf.get("token").asString())
        .header("Idempotency-Key", scenario + "-" + key).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body)));
  }

  private ResultActions update(String url, String key, Object body) throws Exception {
    return mvc.perform(patch(url).cookie(session)
        .header(csrf.get("headerName").asString(), csrf.get("token").asString())
        .header("Idempotency-Key", scenario + "-" + key).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body)));
  }

  private JsonNode readGet(String url) throws Exception {
    return read(mvc.perform(get(url).cookie(session)).andExpect(status().isOk()));
  }

  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }
}

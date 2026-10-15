package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.awaitility.Awaitility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 仅用于隔离MySQL库；用真实行锁和锁等待表证明两条HTTP请求的交错。 */
@RequiredArgsConstructor
final class InterviewWithdrawalExternalRace {
  private final JdbcTemplate jdbc;

  ResultActions run(boolean withdrawalFirst, boolean beforeScheduleUpdate, Callable<ResultActions> withdraw,
      Callable<ResultActions> reschedule) throws Exception {
    String trigger = withdrawalFirst ? """
        CREATE TRIGGER hold_withdrawal_race BEFORE UPDATE ON interview_interview
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF OLD.status='SCHEDULED' AND NEW.status='CANCELLED' THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """ : beforeScheduleUpdate ? """
        CREATE TRIGGER hold_withdrawal_race BEFORE UPDATE ON interview_interview
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF OLD.status='SCHEDULED' AND NEW.status='SCHEDULED'
              AND OLD.scheduled_start_at<>NEW.scheduled_start_at THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """ : """
        CREATE TRIGGER hold_withdrawal_race BEFORE INSERT ON interview_schedule_history
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF NEW.action_type='RESCHEDULE' THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """;
    RaceResult result = runLocked(withdrawalFirst, trigger, "interview_interview", withdraw, reschedule);
    if (withdrawalFirst) {
      result.competing().andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_SCHEDULED"));
    } else {
      result.competing().andExpect(status().isOk())
          .andExpect(jsonPath("$.interview.status").value("SCHEDULED"));
    }
    return result.withdrawal();
  }

  RaceResult runCreation(boolean withdrawalFirst, Callable<ResultActions> withdraw,
      Callable<ResultActions> create) throws Exception {
    String trigger = withdrawalFirst ? """
        CREATE TRIGGER hold_withdrawal_race BEFORE UPDATE ON submission_client_submission
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF OLD.delivery_status='SENT' AND NEW.delivery_status='WITHDRAWN' THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """ : """
        CREATE TRIGGER hold_withdrawal_race BEFORE INSERT ON interview_interview
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF NEW.client_submission_id IS NOT NULL THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """;
    RaceResult result = runLocked(withdrawalFirst, trigger, "submission_client_submission", withdraw, create);
    if (withdrawalFirst) {
      result.competing().andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("CLIENT_SUBMISSION_MISMATCH"));
    } else {
      result.competing().andExpect(status().isCreated())
          .andExpect(jsonPath("$.interview.status").value("SCHEDULED"));
    }
    return result;
  }

  record RaceResult(ResultActions withdrawal, ResultActions competing) {}

  RaceResult runCancellation(boolean withdrawalFirst, Callable<ResultActions> withdraw,
      Callable<ResultActions> cancel) throws Exception {
    String trigger = """
        CREATE TRIGGER hold_withdrawal_race BEFORE UPDATE ON interview_interview
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF OLD.status='SCHEDULED' AND NEW.status='CANCELLED' THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """;
    RaceResult result = runLocked(withdrawalFirst, trigger, "recruitment_application", withdraw, cancel);
    if (withdrawalFirst) {
      result.competing().andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_SCHEDULED"));
    } else {
      result.competing().andExpect(status().isOk())
          .andExpect(jsonPath("$.interview.status").value("CANCELLED"));
    }
    return result;
  }

  RaceResult runFeedback(boolean withdrawalFirst, Callable<ResultActions> withdraw,
      Callable<ResultActions> feedback) throws Exception {
    String trigger = """
        CREATE TRIGGER hold_withdrawal_race BEFORE UPDATE ON interview_interview
        FOR EACH ROW BEGIN
          DECLARE gate_value INT;
          IF OLD.status='SCHEDULED' AND NEW.status='%s' THEN
            SELECT marker INTO gate_value FROM withdrawal_race_gate WHERE id=1 FOR UPDATE;
          END IF;
        END
        """.formatted(withdrawalFirst ? "CANCELLED" : "COMPLETED");
    RaceResult result = runLocked(withdrawalFirst, trigger, "recruitment_application", withdraw, feedback);
    if (withdrawalFirst) {
      result.competing().andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("INTERVIEW_CANCELLED"));
    } else {
      result.competing().andExpect(status().isOk())
          .andExpect(jsonPath("$.feedback.status").value("SUBMITTED"))
          .andExpect(jsonPath("$.currentVersion.versionNo").value(1));
    }
    return result;
  }

  private RaceResult runLocked(boolean withdrawalFirst, String trigger, String waitingTable,
      Callable<ResultActions> withdraw, Callable<ResultActions> competing) throws Exception {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
        .matches("hr_platform_it_[0-9a-f]{32}");
    jdbc.execute("CREATE TABLE withdrawal_race_gate (id INT PRIMARY KEY, marker INT NOT NULL) ENGINE=InnoDB");
    jdbc.update("INSERT INTO withdrawal_race_gate VALUES(1,1)");
    try {
      jdbc.execute(trigger);
      try (var blocker = jdbc.getDataSource().getConnection()) {
        blocker.setAutoCommit(false);
        try (var statement = blocker.prepareStatement("SELECT marker FROM withdrawal_race_gate WHERE id=1 FOR UPDATE");
             var row = statement.executeQuery()) {
          assertThat(row.next()).isTrue();
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
          try {
            var first = executor.submit(withdrawalFirst ? withdraw : competing);
            awaitWaitingOn(blocker, "withdrawal_race_gate");
            var second = executor.submit(withdrawalFirst ? competing : withdraw);
            awaitWaitingOn(blocker, waitingTable);
            assertThat(first.isDone()).isFalse();
            assertThat(second.isDone()).isFalse();
            blocker.commit();
            ResultActions withdrawal = (withdrawalFirst ? first : second).get(20, TimeUnit.SECONDS);
            ResultActions other = (withdrawalFirst ? second : first).get(20, TimeUnit.SECONDS);
            withdrawal.andExpect(status().isOk());
            return new RaceResult(withdrawal, other);
          } finally {
            // 先释放闸门，再等待执行器收敛，失败路径也不能持锁等待工作线程。
            blocker.rollback();
          }
        }
      }
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS hold_withdrawal_race");
      jdbc.execute("DROP TABLE withdrawal_race_gate");
    }
  }

  private void awaitWaitingOn(Connection blocker, String table) {
    // 观察锁等待复用闸门连接，不额外占用两个真实业务事务需要的连接池容量。
    Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(() -> {
      try (var statement = blocker.prepareStatement("""
          SELECT COUNT(*) FROM performance_schema.data_locks
          WHERE OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME=? AND LOCK_STATUS='WAITING'
          """)) {
        statement.setString(1, table);
        try (var rows = statement.executeQuery()) {
          return rows.next() && rows.getInt(1) > 0;
        }
      }
    });
  }
}

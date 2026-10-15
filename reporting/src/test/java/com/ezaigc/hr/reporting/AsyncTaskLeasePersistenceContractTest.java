package com.ezaigc.hr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.domain.entity.AsyncTaskLeaseRow;
import com.ezaigc.hr.reporting.mapper.AsyncTaskAttemptMapper;
import com.ezaigc.hr.reporting.mapper.AsyncTaskMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AsyncTaskLeasePersistenceContractTest {

  @Test
  void schemaPersistsAndIndexesTaskLeaseDeadline() throws IOException {
    String schema =
        Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));

    assertThat(schema)
        .contains("lease_until          TIMESTAMP(6) NULL COMMENT '当前处理租约到期时间'")
        .contains("ix_reporting_task_lease (task_type, status, lease_until, id)");
  }

  @Test
  void existingDatabasesHaveAStandaloneRerunnableLeaseUpgrade() throws IOException {
    String upgrade =
        Files.readString(
            repositoryRoot()
                .resolve(
                    "bootstrap/src/test/resources/db/legacy-upgrades/hr_ai_upgrade_20260907_01__async_task_lease.sql"));

    assertThat(upgrade)
        .contains("information_schema.COLUMNS", "information_schema.STATISTICS")
        .contains("TABLE_SCHEMA = DATABASE()", "TABLE_NAME = 'reporting_async_task'")
        .contains("COLUMN_NAME = 'lease_until'", "INDEX_NAME = 'ix_reporting_task_lease'")
        .contains("ADD COLUMN lease_until TIMESTAMP(6) NULL")
        .contains("ADD INDEX ix_reporting_task_lease (task_type, status, lease_until, id)")
        .contains("PREPARE", "EXECUTE", "DEALLOCATE PREPARE")
        .doesNotContain("DROP TABLE", "DROP COLUMN", "TRUNCATE", "UPDATE reporting_async_task");
    assertThat(upgrade.split("TABLE_SCHEMA = DATABASE\\(\\)", -1)).hasSize(3);
    assertThat(upgrade.split("'DO 0'", -1)).hasSize(3);
  }

  @Test
  void claimAndRecoveryLockRowsAndPersistLeaseDeadline() throws ReflectiveOperationException {
    Method recoveryMethod =
        AsyncTaskMapper.class.getMethod(
            "findExpiredLeasesForUpdate", String.class, Instant.class, int.class);
    String recoverySql = selectSql(recoveryMethod);
    String claimSql =
        updateSql(
            AsyncTaskMapper.class.getMethod(
                "markRunning",
                String.class,
                Instant.class,
                Instant.class,
                String.class));

    assertThat(recoverySql)
        .contains("SELECT id,task_type,resource_id,status,attempt_count,max_attempts")
        .contains("lease_until IS NULL OR lease_until<=#{now}")
        .contains("FOR UPDATE SKIP LOCKED");
    assertThat(recoveryMethod.getGenericReturnType().getTypeName())
        .contains(AsyncTaskLeaseRow.class.getName());
    assertThat(claimSql)
        .contains("attempt_count=attempt_count+1")
        .contains("lease_until=#{leaseUntil}")
        .contains("attempt_count<max_attempts");
  }

  @Test
  void workerWritesAreFencedByAttemptAndUnexpiredLease()
      throws ReflectiveOperationException {
    String progressSql =
        updateSql(
            AsyncTaskMapper.class.getMethod(
                "updateProgress",
                String.class,
                int.class,
                int.class,
                Instant.class,
                Instant.class,
                String.class));
    String successSql =
        updateSql(
            AsyncTaskMapper.class.getMethod(
                "markSucceeded",
                String.class,
                int.class,
                String.class,
                Instant.class,
                String.class));
    String renewalSql =
        updateSql(
            AsyncTaskMapper.class.getMethod(
                "renewLease",
                String.class,
                int.class,
                Instant.class,
                Instant.class,
                String.class));
    String attemptSuccessSql =
        updateSql(
            AsyncTaskAttemptMapper.class.getMethod(
                "markSucceeded", String.class, int.class, Instant.class));

    assertThat(progressSql)
        .contains("attempt_count=#{attemptNo}")
        .contains("lease_until>#{now}")
        .contains("lease_until=#{leaseUntil}");
    assertThat(successSql)
        .contains("attempt_count=#{attemptNo}")
        .contains("lease_until>#{now}")
        .contains("lease_until=NULL");
    assertThat(renewalSql)
        .contains("attempt_count=#{attemptNo}")
        .contains("lease_until>#{now}")
        .contains("lease_until=#{leaseUntil}");
    assertThat(attemptSuccessSql)
        .contains("attempt_no=#{attemptNo}")
        .contains("version=version+1");
  }

  private static String selectSql(Method method) {
    return String.join(" ", method.getAnnotation(Select.class).value());
  }

  private static String updateSql(Method method) {
    return String.join(" ", method.getAnnotation(Update.class).value());
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("docs/sql"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }
}

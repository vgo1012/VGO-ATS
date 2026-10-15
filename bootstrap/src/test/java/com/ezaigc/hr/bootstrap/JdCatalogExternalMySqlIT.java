package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCatalogMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdVersionMapper;
import com.ezaigc.hr.recruiting.demand.mapper.JdEntityMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.JdEntityPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.demand.service.impl.JdVersionPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.ApplicationPersistenceServiceImpl;
import com.ezaigc.hr.intelligence.matching.mapper.ScreeningAdmissionMapper;
import com.ezaigc.hr.intelligence.matching.service.impl.ScreeningAdmissionPersistenceServiceImpl;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

/** 在随机临时 MySQL 库验证独立 JD 库的部署、历史升级和查询契约。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class JdCatalogExternalMySqlIT {

  private static final String OWNER = "O".repeat(26);
  private static final String OTHER_ACTOR = "A".repeat(26);
  private static final String DEPARTMENT = "D".repeat(26);
  private static final String CLIENT_ONE = "C".repeat(26);
  private static final String CLIENT_TWO = "E".repeat(26);

  @Test
  void deletionUsageQueriesIncludeOldVersionsAndOtherOwnersButExcludeTerminalRecords() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      var jdbc = jdbc(connection);
      // 此测试仅验证占用查询，不构造无关评分/工作流；禁用外键仅限随机隔离库的当前连接。
      jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
      String jdId = "J".repeat(26), oldVersion = "V".repeat(26), currentVersion = "W".repeat(26);
      for (var entry : List.of(Map.entry(oldVersion, 1), Map.entry(currentVersion, 2))) {
        jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type)"
            + " VALUES(?,?,?,'合成岗位','合成正文','DRAFT','MANUAL')", entry.getKey(), jdId, entry.getValue());
      }
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("jd-deletion-it", new JdbcTransactionFactory(),
          new SingleConnectionDataSource(connection, true)));
      configuration.addMapper(JdVersionMapper.class);
      configuration.addMapper(ApplicationMapper.class);
      configuration.addMapper(ScreeningAdmissionMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var versions = new JdVersionPersistenceServiceImpl();
        ReflectionTestUtils.setField(versions, "baseMapper", session.getMapper(JdVersionMapper.class));
        var applications = new ApplicationPersistenceServiceImpl();
        ReflectionTestUtils.setField(applications, "baseMapper", session.getMapper(ApplicationMapper.class));
        var screening = new ScreeningAdmissionPersistenceServiceImpl();
        ReflectionTestUtils.setField(screening, "baseMapper", session.getMapper(ScreeningAdmissionMapper.class));
        var versionIds = versions.findVersionIds(jdId);
        assertThat(versionIds).containsExactlyInAnyOrder(oldVersion, currentVersion);
        assertThat(screening.hasActiveAdmissions(List.of())).isFalse();
        assertThat(screening.hasActiveAdmissions(versionIds)).isFalse();
        assertThat(applications.hasActiveApplicationsForJd(jdId)).isFalse();

        jdbc.update("INSERT INTO matching_screening_admission(id,candidate_id,resume_version_id,jd_version_id,"
            + "default_match_run_id,admitted_by_user_id,admitted_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
            "S".repeat(26), "T".repeat(26), "R".repeat(26), oldVersion, "M".repeat(26), OTHER_ACTOR);
        jdbc.update("INSERT INTO recruitment_application(id,candidate_id,jd_id,jd_version_id,resume_version_id,"
            + "workflow_template_version_id,match_plan_version_id,recruitment_type,current_stage,owner_user_id,owner_department_id)"
            + " VALUES(?,?,?,?,?,?,?,'INTERNAL','PATH_SELECTION',?,?)", "A".repeat(26), "T".repeat(26),
            jdId, oldVersion, "R".repeat(26), "F".repeat(26), "M".repeat(26), OTHER_ACTOR, DEPARTMENT);
        session.clearCache();
        assertThat(screening.hasActiveAdmissions(versionIds)).isTrue();
        assertThat(screening.hasActiveAdmissions(List.of(currentVersion))).isFalse();
        assertThat(applications.hasActiveApplicationsForJd(jdId)).isTrue();
        assertThat(applications.hasActiveApplicationsForJd("K".repeat(26))).isFalse();

        jdbc.update("UPDATE matching_screening_admission SET returned_at=CURRENT_TIMESTAMP(6)");
        for (String terminal : List.of("TERMINATED", "ONBOARDED")) {
          jdbc.update("UPDATE recruitment_application SET current_stage=?,active_slot=NULL,terminal_at=CURRENT_TIMESTAMP(6)", terminal);
          session.clearCache();
          assertThat(screening.hasActiveAdmissions(versionIds)).isFalse();
          assertThat(applications.hasActiveApplicationsForJd(jdId)).isFalse();
        }
      }
    });
  }

  @Test
  void independentJdsMayShareTitleAndDistinctionWhileKeepingDifferentCodes() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = jdbc(connection);
      insertDirectoryFixtures(jdbc);
      insertJd(jdbc, "J".repeat(26), "JD_ONE", null, false, false, "INTERNAL");
      insertJd(jdbc, "K".repeat(26), "JD_TWO", null, false, false, "INTERNAL");
      jdbc.update("UPDATE demand_jd SET title='共同岗位',distinction=NULL WHERE id IN (?,?)",
          "J".repeat(26), "K".repeat(26));
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd WHERE title='共同岗位'", Integer.class))
          .isEqualTo(2);
      assertThat(jdEntityMapper(connection).selectById("J".repeat(26)).getCode()).isEqualTo("JD_ONE");
      assertThat(jdEntityMapper(connection).selectById("K".repeat(26)).getCode()).isEqualTo("JD_TWO");
    });
  }

  @Test
  void metadataUpdateChangesCurrentDepartmentWithoutRewritingSourceDepartment() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = jdbc(connection);
      insertDirectoryFixtures(jdbc);
      jdbc.update(
          "INSERT INTO identity_department(id,code,name,status) VALUES(?,?,'支持部门','ACTIVE')",
          "X".repeat(26), "DEP_SUPPORT");
      jdbc.update(
          "INSERT INTO identity_user_account(id,display_name,work_email,normalized_email,credential_hash,home_department_id,status,must_change_password,registered_at)"
              + " VALUES(?,'新所属人','new-owner@example.invalid','new-owner@example.invalid','{noop}unused',?,'ACTIVE',FALSE,CURRENT_TIMESTAMP(6))",
          "N".repeat(26), "X".repeat(26));
      insertJd(jdbc, "J".repeat(26), "JD_DEPARTMENT", null, false, false, "INTERNAL");
      jdbc.update("UPDATE demand_jd SET source_owner_department_id=? WHERE id=?",
          DEPARTMENT, "J".repeat(26));
      var persistence = new JdEntityPersistenceServiceImpl();
      ReflectionTestUtils.setField(persistence, "baseMapper", jdEntityMapper(connection));

      assertThat(persistence.updateRecruitmentMetadata(
          "J".repeat(26), 1, 45, "N".repeat(26), "X".repeat(26), true, 0L, OWNER, Instant.now()))
          .isOne();
      assertThat(persistence.updateRecruitmentMetadata(
          "J".repeat(26), 1, 45, OWNER, DEPARTMENT, false, 0L, OWNER, Instant.now()))
          .isZero();

      assertThat(jdbc.queryForMap(
          "SELECT owner_user_id,owner_department_id,source_owner_department_id,content_shared,version FROM demand_jd WHERE id=?",
          "J".repeat(26)))
          .containsEntry("owner_user_id", "N".repeat(26))
          .containsEntry("owner_department_id", "X".repeat(26))
          .containsEntry("source_owner_department_id", DEPARTMENT)
          .containsEntry("content_shared", true)
          .containsEntry("version", 1L);

      JdEntity changed = new JdEntity();
      changed.setId("J".repeat(26));
      changed.setTitle("更新后的正文标题");
      changed.setRecruitmentType("INTERNAL");
      changed.setContentShared(false);
      changed.setHeadcount(1);
      changed.setRecruitmentCycleDays(45);
      changed.setDistinction("更新后的区分说明");
      changed.setCurrentVersionNo(2);
      changed.setOwnerUserId(OWNER);
      changed.setOwnerDepartmentId(DEPARTMENT);
      changed.setUpdateId(OWNER);
      changed.setUpdateTime(Instant.now());
      assertThat(persistence.updateCatalog(changed, 1L)).isOne();
      assertThat(jdbc.queryForMap(
          "SELECT title,owner_user_id,owner_department_id,source_owner_department_id,content_shared,version FROM demand_jd WHERE id=?",
          "J".repeat(26)))
          .containsEntry("title", "更新后的正文标题")
          .containsEntry("owner_user_id", OWNER)
          .containsEntry("owner_department_id", DEPARTMENT)
          .containsEntry("source_owner_department_id", DEPARTMENT)
          .containsEntry("content_shared", false)
          .containsEntry("version", 2L);
    });
  }

  @Test
  void publicContentSourcesExcludeAppliedCopiesWithoutExpandingPrivateJdAccess() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = jdbc(connection);
      insertDirectoryFixtures(jdbc);
      jdbc.update("INSERT INTO demand_jd_code_option(id,kind,code,name) VALUES(?,'REGION','001','北京')",
          "R".repeat(26));
      for (String letter : List.of("J", "K", "L", "M", "N", "P")) {
        String id = letter.repeat(26);
        insertJd(jdbc, id, "JD_" + letter, null, true, false, "INTERNAL");
        jdbc.update("UPDATE demand_jd SET content_shared=TRUE,work_region_code='001' WHERE id=?", id);
        jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type)"
                + " VALUES(?,?,1,?,?,'PUBLISHED','MANUAL')",
            "V" + id.substring(1), id, "JD_" + letter + " 岗位", "原文-" + letter);
      }
      String rootId = "J".repeat(26);
      String copyId = "L".repeat(26);
      jdbc.update("UPDATE demand_jd SET content_shared=FALSE WHERE id=?", "K".repeat(26));
      jdbc.update("UPDATE demand_jd SET applied_from_jd_version_id=?,owner_department_id=? WHERE id=?",
          "V" + rootId.substring(1), "X".repeat(26), copyId);
      jdbc.update("UPDATE demand_jd SET status='CLOSED' WHERE id=?", "M".repeat(26));
      jdbc.update("UPDATE demand_jd SET deleted=TRUE WHERE id=?", "N".repeat(26));
      jdbc.update("UPDATE demand_jd_version SET status='DRAFT' WHERE jd_id=?", "P".repeat(26));
      jdbc.update("UPDATE demand_jd SET create_time='2026-10-10 01:00:00' WHERE id=?", rootId);
      jdbc.update("UPDATE demand_jd SET create_time='2026-10-10 02:00:00' WHERE id=?", copyId);

      DemandCatalogMapper mapper = mapper(connection);
      assertThat(mapper.findPublicJdSources(null, "ACTIVE", "PUBLISHED", null, null, 50))
          .extracting(PublicJdSourceDTO::getId).containsExactly(rootId);
      assertThat(mapper.countPublicJdSources(null, "ACTIVE", "PUBLISHED")).isOne();
      assertThat(mapper.findPublicJdSources("北京", "ACTIVE", "PUBLISHED", null, null, 50)).hasSize(1);
      assertThat(mapper.findPublicJdSources("JD_J", "ACTIVE", "PUBLISHED", null, null, 50))
          .extracting(PublicJdSourceDTO::getId).containsExactly(rootId);
      PublicJdSourceDTO first = mapper.findPublicJdSources(null, "ACTIVE", "PUBLISHED", null, null, 1).getFirst();
      assertThat(first.getRawText()).isNull();
      assertThat(first.getOwnerUserId()).isEqualTo(OWNER);
      assertThat(first.getOwnerDepartmentId()).isEqualTo(DEPARTMENT);
      assertThat(mapper.findPublicJdSources(null, "ACTIVE", "PUBLISHED", first.getCreateTime(), first.getId(), 1))
          .isEmpty();
      assertThat(mapper.findPublicJdSource(copyId, "ACTIVE", "PUBLISHED")).isNull();
      assertThat(mapper.findPublicJdSource("K".repeat(26), "ACTIVE", "PUBLISHED")).isNull();
      assertThat(mapper.findJds(null, null, null, OTHER_ACTOR, false, Set.of(), Set.of(), null, null, 50)).isEmpty();

      jdbc.update("INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type)"
              + " VALUES(?,?,2,?,'更新后的公共原文','PUBLISHED','MANUAL')",
          "Z".repeat(26), rootId, "JD_J 岗位");
      jdbc.update("UPDATE demand_jd SET current_version_no=2 WHERE id=?", rootId);
      PublicJdSourceDTO latest = mapper.findPublicJdSource(rootId, "ACTIVE", "PUBLISHED");
      assertThat(latest.getCurrentVersionId()).isEqualTo("Z".repeat(26));
      assertThat(latest.getRawText()).isEqualTo("更新后的公共原文");
      jdbc.update("UPDATE demand_jd SET content_shared=FALSE WHERE id=?", rootId);
      assertThat(mapper.findPublicJdSource(rootId, "ACTIVE", "PUBLISHED")).isNull();
      assertThat(mapper.findPublicJdSource(copyId, "ACTIVE", "PUBLISHED")).isNull();
      assertThat(mapper.countPublicJdSources(null, "ACTIVE", "PUBLISHED")).isZero();
    });
  }

  @Test
  void publicContentUpgradePreservesHistoryAndSharedChoicesAfterPartialReruns() throws Exception {
    withDatabase(connection -> {
      execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
      JdbcTemplate jdbc = jdbc(connection);
      insertDirectoryFixtures(jdbc);
      insertJd(jdbc, "J".repeat(26), "JD_HISTORY", null, true, false, "INTERNAL");
      jdbc.execute("ALTER TABLE demand_jd DROP INDEX ix_demand_jd_public_sources,DROP COLUMN content_shared");
      Path upgrade = sqlRoot().resolve("hr_ai_upgrade_20261010_01__public_jd_content.sql");
      execute(connection, upgrade);
      execute(connection, upgrade);
      assertThat(jdbc.queryForMap("SELECT code,owner_user_id,public_visible,content_shared FROM demand_jd WHERE id=?",
          "J".repeat(26)))
          .containsEntry("code", "JD_HISTORY")
          .containsEntry("owner_user_id", OWNER)
          .containsEntry("public_visible", true)
          .containsEntry("content_shared", false);
      jdbc.update("UPDATE demand_jd SET content_shared=TRUE WHERE id=?", "J".repeat(26));
      jdbc.execute("ALTER TABLE demand_jd DROP INDEX ix_demand_jd_public_sources");
      execute(connection, upgrade);
      execute(connection, upgrade);
      assertThat(jdbc.queryForObject("SELECT content_shared FROM demand_jd WHERE id=?", Boolean.class,
          "J".repeat(26))).isTrue();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS"
          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_public_sources'",
          Integer.class)).isEqualTo(5);
      jdbc.execute("ALTER TABLE demand_jd MODIFY content_shared BOOLEAN NULL DEFAULT FALSE"
          + " COMMENT '主动共享 JD 内容，不授予招聘记录权限'");
      assertThatThrownBy(() -> execute(connection, upgrade)).hasStackTraceContaining("incompatible definition");
      assertThat(jdbc.queryForObject("SELECT content_shared FROM demand_jd WHERE id=?", Boolean.class,
          "J".repeat(26))).isTrue();
    });
  }

  @Test
  void baselineSupportsFrozenJdOwnershipVisibilityAndDemandlessWorkflowRows() throws Exception {
    withDatabase(
        connection -> {
          execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(connection);
          insertDirectoryFixtures(jdbc);
          insertClient(jdbc, CLIENT_ONE, "CLIENT_ONE");
          insertClient(jdbc, CLIENT_TWO, "CLIENT_TWO");

          insertJd(jdbc, "J".repeat(26), "JD_PUBLIC", CLIENT_ONE, true, false, "EXTERNAL");
          jdbc.update("UPDATE demand_jd SET recruitment_cycle_days=45 WHERE id=?", "J".repeat(26));
          insertJd(jdbc, "K".repeat(26), "JD_PRIVATE", CLIENT_ONE, false, false, "INTERNAL");
          insertJd(jdbc, "L".repeat(26), "JD_DELETED", CLIENT_ONE, true, true, "EXTERNAL");
          jdbc.update(
              "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,client_id,recruitment_type,content_owner_user_id,content_fingerprint,create_id)"
                  + " VALUES(?,?,?,?,?,'PUBLISHED','MANUAL',?,?,?,?,?)",
              "V".repeat(26),
              "J".repeat(26),
              1,
              "公开岗位",
              "合成 JD 正文",
              CLIENT_ONE,
              "EXTERNAL",
              OWNER,
              "h".repeat(64),
              OWNER);
          assertContentFingerprintUniqueness(jdbc);

          DemandCatalogMapper mapper = mapper(connection);
          assertThat(mapper.findJd("J".repeat(26)).getRecruitmentCycleDays()).isEqualTo(45);
          assertThat(
                  mapper.findJds(
                      null, null, null, OTHER_ACTOR, false, Set.of(), Set.of(), null, null, 50))
              .extracting(JdEntity::getCode)
              .containsExactly("JD_PUBLIC");
          assertThat(
                  mapper.findJds(
                      null, null, null, OWNER, false, Set.of(), Set.of(), null, null, 50))
              .extracting(JdEntity::getCode)
              .containsExactly("JD_PRIVATE", "JD_PUBLIC");

          jdbc.update(
              "UPDATE demand_jd SET client_id=?,recruitment_type='INTERNAL' WHERE id=?",
              CLIENT_TWO,
              "J".repeat(26));
          assertThat(
                  jdbc.queryForMap(
                      "SELECT client_id,recruitment_type FROM demand_jd_version WHERE id=?",
                      "V".repeat(26)))
              .containsEntry("client_id", CLIENT_ONE)
              .containsEntry("recruitment_type", "EXTERNAL");

          assertNullable(jdbc, "recruitment_application", "demand_id");
          assertNullable(jdbc, "submission_client_submission", "demand_id");
          assertNullable(jdbc, "submission_client_submission", "position_id");
          assertDemandlessRowsCanBeWritten(jdbc);
          assertSourceUniqueness(jdbc);
        });
  }

  @Test
  void activeExactVersionLookupIncludesForceNewRowsAndExcludesDeletedJds() throws Exception {
    withDatabase(
        connection -> {
          execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(connection);
          insertDirectoryFixtures(jdbc);
          insertJd(jdbc, "J".repeat(26), "JD_DELETED", null, false, true, "INTERNAL");
          insertJd(jdbc, "K".repeat(26), "JD_ACTIVE", null, false, false, "INTERNAL");
          jdbc.update("UPDATE demand_jd SET title='AI剪辑师',distinction=NULL WHERE id IN (?,?)",
              "J".repeat(26), "K".repeat(26));
          String insertVersion =
              "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,"
                  + "recruitment_type,content_owner_user_id,content_fingerprint,create_id)"
                  + " VALUES(?,?,1,'AI剪辑师',?,'PUBLISHED','MANUAL','INTERNAL',?,NULL,?)";
          jdbc.update(insertVersion, "V".repeat(26), "J".repeat(26), "第一行\r\n第二行", OWNER, OWNER);
          jdbc.update(insertVersion, "W".repeat(26), "K".repeat(26), "第一行\r\n第二行", OWNER, OWNER);

          var exact = jdVersionMapper(connection).findActiveExactVersion(
              OWNER, DEPARTMENT, "INTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false);

          assertThat(exact.getId()).isEqualTo("W".repeat(26));
          assertThat(exact.getContentFingerprint()).isNull();
          var mapper = jdVersionMapper(connection);
          assertThat(mapper.findActiveExactVersion(
              OWNER, DEPARTMENT, "INTERNAL", "ai剪辑师", "第一行\n第二行", null, null, false)).isNull();
          assertThat(mapper.findActiveExactVersion(
              OWNER, DEPARTMENT, "INTERNAL", "AI剪辑师", "第一行\n第二行 ", null, null, false)).isNull();
          assertThat(mapper.findActiveExactVersion(
              OWNER, DEPARTMENT, "INTERNAL", "AI剪辑师", "第一行\n第二行", null, "", false)).isNull();
          assertThat(mapper.findActiveExactVersion(
              OWNER, "X".repeat(26), "INTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false)).isNull();
          assertThat(mapper.findActiveExactVersion(
              OWNER, DEPARTMENT, "EXTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false)).isNull();
        });
  }

  @Test
  void tombstoneLockCurrentReadSeesAReplacementCommittedAfterTheOldSnapshot() throws Exception {
    withDatabase(
        blocker -> {
          execute(blocker, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate blockerJdbc = jdbc(blocker);
          insertDirectoryFixtures(blockerJdbc);
          insertJd(blockerJdbc, "J".repeat(26), "JD_DELETED", null, false, true, "INTERNAL");
          blockerJdbc.update("UPDATE demand_jd SET title='AI剪辑师',distinction=NULL WHERE id=?",
              "J".repeat(26));
          blockerJdbc.update(
              "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,"
                  + "recruitment_type,content_owner_user_id,content_fingerprint,create_id)"
                  + " VALUES(?,?,1,'AI剪辑师','旧正文','PUBLISHED','MANUAL','INTERNAL',?,?,?)",
              "V".repeat(26), "J".repeat(26), OWNER, "f".repeat(64), OWNER);

          blocker.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
          blocker.setAutoCommit(false);
          blockerJdbc.queryForObject(
              "SELECT id FROM demand_jd WHERE id=? FOR UPDATE", String.class, "J".repeat(26));

          try (Connection waiter = DriverManager.getConnection(
                   blocker.getMetaData().getURL(), requiredEnvironment("HR_DB_USER"),
                   requiredEnvironment("HR_DB_PASSWORD"));
              var executor = Executors.newSingleThreadExecutor()) {
            waiter.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            waiter.setAutoCommit(false);
            JdbcTemplate waiterJdbc = jdbc(waiter);
            long waiterThreadId = waiterJdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
            var oldSnapshotReady = new CountDownLatch(1);
            var result = executor.submit(() -> {
              waiterJdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_version", Integer.class);
              oldSnapshotReady.countDown();
              waiterJdbc.queryForObject(
                  "SELECT id FROM demand_jd WHERE id=? FOR UPDATE", String.class, "J".repeat(26));
              var exact = jdVersionMapper(waiter).findActiveExactVersionForUpdate(
                  OWNER, DEPARTMENT, "INTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false);
              assertThat(jdVersionMapper(waiter).findActiveExactVersionForUpdate(
                  OWNER, "X".repeat(26), "INTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false)).isNull();
              assertThat(jdVersionMapper(waiter).findActiveExactVersionForUpdate(
                  OWNER, DEPARTMENT, "EXTERNAL", "AI剪辑师", "第一行\n第二行", null, null, false)).isNull();
              waiter.commit();
              return exact;
            });
            assertThat(oldSnapshotReady.await(5, TimeUnit.SECONDS)).isTrue();
            awaitLockWait(blockerJdbc, waiterThreadId);

            insertJd(blockerJdbc, "K".repeat(26), "JD_ACTIVE", null, false, false, "INTERNAL");
            blockerJdbc.update("UPDATE demand_jd SET title='AI剪辑师',distinction=NULL WHERE id=?",
                "K".repeat(26));
            blockerJdbc.update(
                "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,"
                    + "recruitment_type,content_owner_user_id,content_fingerprint,create_id)"
                    + " VALUES(?,?,1,'AI剪辑师',?,'PUBLISHED','MANUAL','INTERNAL',?,NULL,?)",
                "W".repeat(26), "K".repeat(26), "第一行\r\n第二行", OWNER, OWNER);
            blocker.commit();

            assertThat(result.get(5, TimeUnit.SECONDS).getId()).isEqualTo("W".repeat(26));
          } finally {
            if (!blocker.getAutoCommit()) {
              blocker.rollback();
              blocker.setAutoCommit(true);
            }
          }
        });
  }

  @Test
  void concurrentLastHeadcountIssueIsSerializedAndTerminatedOfferReleasesCapacity() throws Exception {
    withDatabase(
        setup -> {
          execute(setup, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(setup);
          insertDirectoryFixtures(jdbc);
          insertJd(jdbc, "J".repeat(26), "JD_HC", null, false, false, "INTERNAL");
          jdbc.update("UPDATE demand_jd SET headcount=1 WHERE id=?", "J".repeat(26));
          jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
          try {
            String insertApplication =
                "INSERT INTO recruitment_application(id,candidate_id,demand_id,jd_id,jd_version_id,resume_version_id,"
                    + "workflow_template_version_id,match_plan_version_id,recruitment_type,current_stage,owner_user_id,owner_department_id)"
                    + " VALUES(?,?,NULL,?,?,?,?,?,'INTERNAL','OFFER',?,?)";
            jdbc.update(insertApplication, "P".repeat(26), "A".repeat(26), "J".repeat(26),
                "V".repeat(26), "R".repeat(26), "X".repeat(26), "M".repeat(26), OWNER, DEPARTMENT);
            jdbc.update(insertApplication, "Q".repeat(26), "B".repeat(26), "J".repeat(26),
                "W".repeat(26), "S".repeat(26), "Y".repeat(26), "N".repeat(26), OWNER, DEPARTMENT);
          } finally {
            jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
          }
          String url = setup.getMetaData().getURL();
          var ready = new CountDownLatch(2);
          var start = new CountDownLatch(1);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> issueLastHeadcount(url, "P".repeat(26), "R".repeat(26), ready, start));
            var second = executor.submit(() -> issueLastHeadcount(url, "Q".repeat(26), "S".repeat(26), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
          }

          assertThat(jdbc.queryForObject(
              "SELECT COUNT(*) FROM recruitment_offer_status_record WHERE status='ISSUED'", Integer.class)).isOne();
          String applicationId = jdbc.queryForObject(
              "SELECT application_id FROM recruitment_offer_status_record WHERE status='ISSUED'", String.class);
          jdbc.update("UPDATE recruitment_offer_status_record SET create_time='2026-09-18 00:00:00' WHERE application_id=?", applicationId);
          var issued = new DemandCatalogAssembler().toJdResponse(mapper(setup).findJd("J".repeat(26)));
          assertThat(issued.getOccupiedHeadcount()).isEqualTo(1);
          assertThat(issued.getOnboardedHeadcount()).isZero();
          jdbc.update("INSERT INTO recruitment_offer_status_record(id,application_id,record_type,status,reason,source,occurred_at,operated_by_user_id,create_time)"
                  + " VALUES(?,?,'STATUS','ONBOARDED','实际入职','MANUAL',UTC_TIMESTAMP(6),?,'2026-09-18 00:00:01')",
              "U".repeat(26), applicationId, OWNER);
          var onboarded = new DemandCatalogAssembler().toJdResponse(mapper(setup).findJd("J".repeat(26)));
          assertThat(onboarded.getOccupiedHeadcount()).isEqualTo(1);
          assertThat(onboarded.getOnboardedHeadcount()).isEqualTo(1);
          assertThat(mapper(setup).findJds(null, null, null, OWNER, true, Set.of(), Set.of(), null, null, 50))
              .filteredOn(jd -> jd.getId().equals("J".repeat(26)))
              .singleElement().satisfies(jd -> assertThat(jd.getOnboardedHeadcount()).isEqualTo(1));
          jdbc.update("INSERT INTO recruitment_offer_status_record(id,application_id,record_type,status,reason,source,occurred_at,operated_by_user_id,create_time)"
                  + " VALUES(?,?,'STATUS','TERMINATED','测试释放','MANUAL',UTC_TIMESTAMP(6),?,'2026-09-18 00:00:02')",
              "T".repeat(26), applicationId, OWNER);
          assertThat(mapper(setup).countOccupiedHeadcount("J".repeat(26))).isZero();
          assertThat(mapper(setup).findJd("J".repeat(26)).getOnboardedHeadcount()).isZero();
        });
  }

  private static boolean issueLastHeadcount(
      String url, String applicationId, String offerId, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    try (Connection connection = DriverManager.getConnection(
        url, requiredEnvironment("HR_DB_USER"), requiredEnvironment("HR_DB_PASSWORD"))) {
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setAutoCommit(false);
      JdbcTemplate jdbc = jdbc(connection);
      jdbc.queryForObject("SELECT COUNT(*) FROM demand_jd_version", Integer.class);
      ready.countDown();
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
      jdbc.queryForObject("SELECT id FROM demand_jd WHERE id=? FOR UPDATE", String.class, "J".repeat(26));
      if (mapper(connection).findOccupiedApplicationIdsForUpdate("J".repeat(26)).size() >= 1) {
        connection.commit();
        return false;
      }
      jdbc.update("INSERT INTO recruitment_offer_status_record(id,application_id,record_type,status,reason,source,occurred_at,operated_by_user_id)"
              + " VALUES(?,?,'STATUS','ISSUED','测试占用','MANUAL',UTC_TIMESTAMP(6),?)",
          offerId, applicationId, OWNER);
      connection.commit();
      return true;
    }
  }

  @Test
  void headcountUpgradePreservesHistoricalNullAndEnteredValuesAcrossPartialCompletionAndReruns()
      throws Exception {
    withDatabase(
        connection -> {
          execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(connection);
          insertDirectoryFixtures(jdbc);
          insertJd(jdbc, "H".repeat(26), "JD_HISTORICAL_HC", null, false, false, "INTERNAL");
          Path upgrade = sqlRoot().resolve("hr_ai_upgrade_20260914_01__jd_headcount.sql");

          jdbc.execute("ALTER TABLE demand_jd DROP CHECK ck_demand_jd_headcount");
          jdbc.execute("ALTER TABLE demand_jd DROP COLUMN headcount");
          execute(connection, upgrade);
          assertThat(jdbc.queryForObject(
              "SELECT headcount FROM demand_jd WHERE id=?", Integer.class, "H".repeat(26)))
              .isNull();

          jdbc.update("UPDATE demand_jd SET headcount=3 WHERE id=?", "H".repeat(26));
          execute(connection, upgrade);
          assertThat(jdbc.queryForObject(
              "SELECT headcount FROM demand_jd WHERE id=?", Integer.class, "H".repeat(26)))
              .isEqualTo(3);

          jdbc.execute("ALTER TABLE demand_jd DROP CHECK ck_demand_jd_headcount");
          execute(connection, upgrade);
          execute(connection, upgrade);
          assertThat(jdbc.queryForObject(
              "SELECT headcount FROM demand_jd WHERE id=?", Integer.class, "H".repeat(26)))
              .isEqualTo(3);
          assertThat(jdbc.queryForObject(
              "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"
                  + " AND TABLE_NAME='demand_jd' AND CONSTRAINT_NAME='ck_demand_jd_headcount'",
              Integer.class)).isOne();
        });
  }

  @Test
  void recruitmentCycleUpgradePreservesHistoricalNullAndEnteredValuesAcrossReruns()
      throws Exception {
    withDatabase(
        connection -> {
          execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(connection);
          insertDirectoryFixtures(jdbc);
          insertJd(jdbc, "C".repeat(26), "JD_HISTORICAL_CYCLE", null, false, false, "INTERNAL");
          Path upgrade = sqlRoot().resolve("hr_ai_upgrade_20260914_02__jd_recruitment_cycle.sql");

          jdbc.execute("ALTER TABLE demand_jd DROP CHECK ck_demand_jd_recruitment_cycle");
          jdbc.execute("ALTER TABLE demand_jd DROP COLUMN recruitment_cycle_days");
          execute(connection, upgrade);
          assertThat(jdbc.queryForObject(
              "SELECT recruitment_cycle_days FROM demand_jd WHERE id=?", Integer.class,
              "C".repeat(26))).isNull();

          jdbc.update("UPDATE demand_jd SET recruitment_cycle_days=45 WHERE id=?", "C".repeat(26));
          execute(connection, upgrade);
          jdbc.execute("ALTER TABLE demand_jd DROP CHECK ck_demand_jd_recruitment_cycle");
          execute(connection, upgrade);
          assertThat(jdbc.queryForObject(
              "SELECT recruitment_cycle_days FROM demand_jd WHERE id=?", Integer.class,
              "C".repeat(26))).isEqualTo(45);
          assertThat(jdbc.queryForObject(
              "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()"
                  + " AND TABLE_NAME='demand_jd' AND CONSTRAINT_NAME='ck_demand_jd_recruitment_cycle'",
              Integer.class)).isOne();
        });
  }

  @Test
  void upgradePreservesHistoricalJdsAndSupportsPartialCompletionAndReruns() throws Exception {
    withDatabase(
        connection -> {
          Path baseline = sqlRoot().resolve("hr_ai_complete_schema.sql");
          Path upgrade = legacyUpgrade("hr_ai_upgrade_20260909_01__jd_catalog.sql");
          execute(connection, baseline);
          JdbcTemplate jdbc = jdbc(connection);
          insertDirectoryFixtures(jdbc);
          insertClient(jdbc, CLIENT_ONE, "CLIENT_ONE");
          jdbc.update(
              "INSERT INTO demand_jd(id,title,status,current_version_no,create_id,source_owner_department_id,source_fingerprint)"
                  + " VALUES(?,?,'ACTIVE',1,?,?,?)",
              "H".repeat(26),
              "历史岗位",
              OWNER,
              DEPARTMENT,
              "f".repeat(64));
          jdbc.update(
              "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,content_owner_user_id,content_fingerprint,create_id)"
                  + " VALUES(?,?,1,?,?,'PUBLISHED','MANUAL',?,?,?)",
              "W".repeat(26),
              "H".repeat(26),
              "历史岗位",
              "历史正文",
              OWNER,
              "z".repeat(64),
              OWNER);

          // 还原发布升级前的列集合，并模拟其中一列已由中断的首次执行提前完成。
          jdbc.execute("ALTER TABLE demand_jd DROP FOREIGN KEY fk_demand_jd_client");
          jdbc.execute("ALTER TABLE demand_jd DROP INDEX uk_demand_jd_code");
          jdbc.execute(
              "ALTER TABLE demand_jd DROP COLUMN code,DROP COLUMN distinction,DROP COLUMN client_id,"
                  + "DROP COLUMN owner_user_id,DROP COLUMN owner_department_id,DROP COLUMN recruitment_type,"
                  + "DROP COLUMN public_visible,DROP COLUMN deleted");
          jdbc.execute("ALTER TABLE demand_jd_version DROP FOREIGN KEY fk_demand_jd_version_client");
          jdbc.execute("ALTER TABLE demand_jd_version DROP INDEX uk_jd_version_content");
          jdbc.execute(
              "ALTER TABLE demand_jd_version DROP COLUMN client_id,DROP COLUMN recruitment_type");
          jdbc.execute("ALTER TABLE recruitment_application MODIFY demand_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
          jdbc.execute("ALTER TABLE submission_client_submission MODIFY demand_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
          jdbc.execute("ALTER TABLE submission_client_submission MODIFY position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
          jdbc.execute("ALTER TABLE demand_jd ADD public_visible BOOLEAN NOT NULL DEFAULT FALSE AFTER current_version_no");
          jdbc.execute("DROP TABLE demand_jd_source");
          jdbc.execute("ALTER TABLE reporting_async_task DROP INDEX ix_reporting_task_resource");

          execute(connection, upgrade);
          var upgraded =
              jdbc.queryForMap(
                  "SELECT title,status,current_version_no,public_visible,deleted,recruitment_type FROM demand_jd WHERE id=?",
                  "H".repeat(26));
          assertThat(upgraded)
              .containsEntry("title", "历史岗位")
              .containsEntry("status", "ACTIVE")
              .containsEntry("current_version_no", 1)
              .containsEntry("public_visible", false)
              .containsEntry("deleted", false)
              .containsEntry("recruitment_type", "INTERNAL");
          assertThat(
                  jdbc.queryForMap(
                      "SELECT title,raw_text,status,recruitment_type,content_owner_user_id,content_fingerprint FROM demand_jd_version WHERE id=?",
                      "W".repeat(26)))
              .containsEntry("title", "历史岗位")
              .containsEntry("raw_text", "历史正文")
              .containsEntry("status", "PUBLISHED")
              .containsEntry("recruitment_type", "INTERNAL")
              .containsEntry("content_owner_user_id", OWNER)
              .containsEntry("content_fingerprint", "z".repeat(64));
          assertNullable(jdbc, "recruitment_application", "demand_id");
          assertNullable(jdbc, "submission_client_submission", "demand_id");
          assertNullable(jdbc, "submission_client_submission", "position_id");
          assertThat(
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_source'",
                      Integer.class))
              .isOne();
          assertThat(
                  jdbc.queryForList(
                      "SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()"
                          + " AND TABLE_NAME='reporting_async_task' AND INDEX_NAME='ix_reporting_task_resource' ORDER BY SEQ_IN_INDEX",
                      String.class))
              .containsExactly("task_type", "resource_id", "create_time", "id");

          var completedJd = jdbc.queryForList("SELECT * FROM demand_jd WHERE id=?", "H".repeat(26));
          var completedVersion =
              jdbc.queryForList("SELECT * FROM demand_jd_version WHERE id=?", "W".repeat(26));
          execute(connection, upgrade);
          execute(connection, upgrade);
          assertThat(jdbc.queryForList("SELECT * FROM demand_jd WHERE id=?", "H".repeat(26)))
              .isEqualTo(completedJd);
          assertThat(
                  jdbc.queryForList(
                      "SELECT * FROM demand_jd_version WHERE id=?", "W".repeat(26)))
              .isEqualTo(completedVersion);
        });
  }

  @Test
  void upgradeRejectsAnIncompatiblePartiallyCreatedColumn() throws Exception {
    withDatabase(
        connection -> {
          execute(connection, sqlRoot().resolve("hr_ai_complete_schema.sql"));
          JdbcTemplate jdbc = jdbc(connection);
          jdbc.execute("ALTER TABLE demand_jd MODIFY public_visible BOOLEAN NULL DEFAULT NULL");
          var before =
              jdbc.queryForList(
                  "SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS"
                      + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='public_visible'");

          assertThatThrownBy(
                  () ->
                      execute(
                          connection,
                          legacyUpgrade("hr_ai_upgrade_20260909_01__jd_catalog.sql")))
              .hasRootCauseInstanceOf(SQLException.class)
              .hasStackTraceContaining("incompatible definitions");
          assertThat(
                  jdbc.queryForList(
                      "SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS"
                          + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='public_visible'"))
              .isEqualTo(before);
        });
  }

  private static void insertDirectoryFixtures(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO identity_department(id,code,name,status) VALUES(?,?,'合成部门','ACTIVE')",
        DEPARTMENT,
        "DEP_SYNTHETIC");
    jdbc.update(
        "INSERT INTO identity_user_account(id,display_name,work_email,normalized_email,credential_hash,home_department_id,status,must_change_password,registered_at)"
            + " VALUES(?,'合成负责人','owner@example.invalid','owner@example.invalid','{noop}unused',?,'ACTIVE',FALSE,CURRENT_TIMESTAMP(6))",
        OWNER,
        DEPARTMENT);
  }

  private static void insertClient(JdbcTemplate jdbc, String id, String code) {
    jdbc.update(
        "INSERT INTO demand_client(id,code,name,status,owner_user_id,owner_department_id)"
            + " VALUES(?,?,?,'ACTIVE',?,?)",
        id,
        code,
        code,
        OWNER,
        DEPARTMENT);
  }

  private static void insertJd(
      JdbcTemplate jdbc,
      String id,
      String code,
      String clientId,
      boolean publicVisible,
      boolean deleted,
      String recruitmentType) {
    jdbc.update(
        "INSERT INTO demand_jd(id,code,distinction,public_visible,recruitment_type,deleted,title,status,current_version_no,client_id,owner_user_id,owner_department_id,create_id)"
            + " VALUES(?,?,?, ?,?,?,?,'ACTIVE',1,?,?,?,?)",
        id,
        code,
        code + " 区分说明",
        publicVisible,
        recruitmentType,
        deleted,
        code + " 岗位",
        clientId,
        OWNER,
        DEPARTMENT,
        OWNER);
  }

  private static DemandCatalogMapper mapper(Connection connection) {
    var source = new SingleConnectionDataSource(connection, true);
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("jd-catalog-it", new JdbcTransactionFactory(), source));
    configuration.addMapper(DemandCatalogMapper.class);
    return new MybatisSqlSessionFactoryBuilder()
        .build(configuration)
        .openSession(connection)
        .getMapper(DemandCatalogMapper.class);
  }

  private static JdVersionMapper jdVersionMapper(Connection connection) {
    var source = new SingleConnectionDataSource(connection, true);
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("jd-version-it", new JdbcTransactionFactory(), source));
    configuration.addMapper(JdVersionMapper.class);
    return new MybatisSqlSessionFactoryBuilder()
        .build(configuration)
        .openSession(connection)
        .getMapper(JdVersionMapper.class);
  }

  private static JdEntityMapper jdEntityMapper(Connection connection) {
    var source = new SingleConnectionDataSource(connection, true);
    var configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("jd-entity-it", new JdbcTransactionFactory(), source));
    configuration.addMapper(JdEntityMapper.class);
    return new MybatisSqlSessionFactoryBuilder()
        .build(configuration)
        .openSession(connection)
        .getMapper(JdEntityMapper.class);
  }

  private static void assertNullable(JdbcTemplate jdbc, String table, String column) {
    assertThat(
            jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                    + " AND TABLE_NAME=? AND COLUMN_NAME=?",
                String.class,
                table,
                column))
        .isEqualTo("YES");
  }

  private static void assertDemandlessRowsCanBeWritten(JdbcTemplate jdbc) {
    jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
    try {
      jdbc.update(
          "INSERT INTO recruitment_application(id,candidate_id,demand_id,jd_id,jd_version_id,resume_version_id,"
              + "workflow_template_version_id,match_plan_version_id,recruitment_type,current_stage,owner_user_id,owner_department_id)"
              + " VALUES(?,?,NULL,?,?,?,?,?,'EXTERNAL','PATH_SELECTION',?,?)",
          "P".repeat(26),
          "Q".repeat(26),
          "J".repeat(26),
          "V".repeat(26),
          "R".repeat(26),
          "T".repeat(26),
          "M".repeat(26),
          OWNER,
          DEPARTMENT);
      jdbc.update(
          "INSERT INTO submission_client_submission(id,application_id,candidate_id,demand_id,client_id,project_id,position_id,"
              + "jd_id,jd_version_id,resume_version_id,delivery_status,process_status,owner_user_id,owner_department_id)"
              + " VALUES(?,?,?,NULL,?,NULL,NULL,?,?,?,'DRAFT','ACTIVE',?,?)",
          "S".repeat(26),
          "P".repeat(26),
          "Q".repeat(26),
          CLIENT_ONE,
          "J".repeat(26),
          "V".repeat(26),
          "R".repeat(26),
          OWNER,
          DEPARTMENT);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM recruitment_application WHERE id=? AND demand_id IS NULL",
                  Integer.class,
                  "P".repeat(26)))
          .isOne();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM submission_client_submission WHERE id=? AND demand_id IS NULL AND position_id IS NULL",
                  Integer.class,
                  "S".repeat(26)))
          .isOne();
    } finally {
      jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }
  }

  private static void assertSourceUniqueness(JdbcTemplate jdbc) {
    String sourceKey = "s".repeat(64);
    jdbc.update(
        "INSERT INTO demand_jd_source(id,jd_version_id,source_type,source_key,owner_user_id)"
            + " VALUES(?,?,'MANUAL',?,?)",
        "X".repeat(26),
        "V".repeat(26),
        sourceKey,
        OWNER);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO demand_jd_source(id,jd_version_id,source_type,source_key,owner_user_id)"
                        + " VALUES(?,?,'MANUAL',?,?)",
                    "Y".repeat(26),
                    "V".repeat(26),
                    sourceKey,
                    OWNER))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static void assertContentFingerprintUniqueness(JdbcTemplate jdbc) {
    String insert =
        "INSERT INTO demand_jd_version(id,jd_id,version_no,title,raw_text,status,source_type,content_owner_user_id,content_fingerprint,create_id)"
            + " VALUES(?,?,?,?,'合成正文','PUBLISHED','MANUAL',?,?,?)";
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    "1".repeat(26),
                    "K".repeat(26),
                    1,
                    "同归属重复正文",
                    OWNER,
                    "h".repeat(64),
                    OWNER))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        insert,
        "2".repeat(26),
        "K".repeat(26),
        1,
        "不同归属正文",
        OTHER_ACTOR,
        "h".repeat(64),
        OWNER);
    jdbc.update(
        insert,
        "3".repeat(26),
        "J".repeat(26),
        2,
        "显式独立版本一",
        OWNER,
        null,
        OWNER);
    jdbc.update(
        insert,
        "4".repeat(26),
        "J".repeat(26),
        3,
        "显式独立版本二",
        OWNER,
        null,
        OWNER);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM demand_jd_version WHERE content_fingerprint IS NULL AND content_owner_user_id=?",
                Integer.class,
                OWNER))
        .isEqualTo(2);
  }

  private static JdbcTemplate jdbc(Connection connection) {
    return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
  }

  private static void awaitLockWait(JdbcTemplate jdbc, long connectionId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      var states = jdbc.queryForList(
          "SELECT trx_state FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=?",
          String.class,
          connectionId);
      if (states.contains("LOCK WAIT")) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("等待事务未进入 InnoDB LOCK WAIT");
  }

  private static void execute(Connection connection, Path script) {
    ScriptUtils.executeSqlScript(
        connection,
        new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
  }

  private static Path sqlRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null && !Files.isRegularFile(current.resolve("docs/sql/hr_ai_complete_schema.sql"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("无法定位 SQL 根目录");
    }
    return current.resolve("docs/sql");
  }

  private static Path legacyUpgrade(String name) {
    return sqlRoot()
        .resolve("../../bootstrap/src/test/resources/db/legacy-upgrades")
        .normalize()
        .resolve(name);
  }

  private static void withDatabase(DatabaseWork work) throws Exception {
    String sourceUrl = requiredEnvironment("HR_DB_URL");
    String user = requiredEnvironment("HR_DB_USER");
    String password = requiredEnvironment("HR_DB_PASSWORD");
    URI endpoint = URI.create(sourceUrl.substring("jdbc:".length()));
    if (!"mysql".equals(endpoint.getScheme()) || endpoint.getRawUserInfo() != null) {
      throw new IllegalArgumentException("External tests require a plain MySQL JDBC endpoint");
    }
    String schema = "hr_jd_catalog_it_" + UUID.randomUUID().toString().replace("-", "");
    String isolatedUrl =
        "jdbc:mysql://"
            + endpoint.getRawAuthority()
            + "/"
            + schema
            + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    try (Connection administration = DriverManager.getConnection(sourceUrl, user, password);
        Statement databases = administration.createStatement()) {
      String sourceCatalog = administration.getCatalog();
      databases.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection isolated = DriverManager.getConnection(isolatedUrl, user, password)) {
        assertThat(isolated.getCatalog()).isEqualTo(schema);
        assertThat(isolated.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        assertThat(isolated.getMetaData().getDatabaseMajorVersion()).isGreaterThanOrEqualTo(8);
        work.run(isolated);
      } finally {
        if (!schema.matches("hr_jd_catalog_it_[0-9a-f]{32}")) {
          throw new IllegalStateException("unexpected fixture schema");
        }
        databases.execute("DROP DATABASE `" + schema + "`");
        assertThat(administration.getCatalog()).isEqualTo(sourceCatalog);
      }
    }
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    assertThat(value).as("必须显式配置测试环境变量 %s", name).isNotBlank();
    return value;
  }

  @FunctionalInterface
  private interface DatabaseWork {
    void run(Connection connection) throws Exception;
  }
}

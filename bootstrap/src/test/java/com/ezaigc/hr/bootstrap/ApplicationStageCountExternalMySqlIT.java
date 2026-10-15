package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.application.adapter.IdentityAuthorizationAdapter;
import com.ezaigc.hr.identity.mapper.ObjectGrantMapper;
import com.ezaigc.hr.identity.service.impl.ObjectGrantPersistenceServiceImpl;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationStageCountQuery;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationStageCountResponse;
import com.ezaigc.hr.recruiting.recruitment.mapper.ApplicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.ApplicationPersistenceServiceImpl;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class ApplicationStageCountExternalMySqlIT {
  private static final Instant FROM = Instant.parse("2026-09-06T16:00:00Z");
  private static final Instant TO = FROM.plusSeconds(86400);

  @Test
  void aggregationMatchesObjectAuthorizationAcrossScopesAndGrantPages() throws Exception {
    try (Connection connection = DriverManager.getConnection(System.getenv("HR_DB_URL"),
        System.getenv("HR_DB_USER"), System.getenv("HR_DB_PASSWORD"))) {
      var dataSource = new SingleConnectionDataSource(connection, true);
      var jdbc = new JdbcTemplate(dataSource);
      // 三张表均为连接私有临时表，遮蔽同名业务表且随连接关闭自动释放。
      jdbc.execute("CREATE TEMPORARY TABLE recruitment_application (id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,"
          + "current_stage VARCHAR(32),owner_user_id CHAR(26),owner_department_id CHAR(26),"
          + "jd_id CHAR(26),jd_version_id CHAR(26),create_time TIMESTAMP(6)) ENGINE=InnoDB");
      jdbc.execute("CREATE TEMPORARY TABLE recruitment_application_collaborator (application_id CHAR(26),"
          + "user_id CHAR(26),PRIMARY KEY(application_id,user_id)) ENGINE=InnoDB");
      jdbc.execute("CREATE TEMPORARY TABLE identity_object_grant (id CHAR(26) PRIMARY KEY,user_id CHAR(26),"
          + "object_type VARCHAR(40),object_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin,permission_code VARCHAR(100),"
          + "UNIQUE KEY scope_key(user_id,object_type,object_id,permission_code)) ENGINE=InnoDB");
      var rows = seed(jdbc);
      var configuration = new MybatisConfiguration();
      configuration.setMapUnderscoreToCamelCase(true);
      configuration.setEnvironment(new Environment("stage-count", new JdbcTransactionFactory(), dataSource));
      configuration.addMapper(ApplicationMapper.class);
      configuration.addMapper(ObjectGrantMapper.class);
      try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
        var applicationMapper = session.getMapper(ApplicationMapper.class);
        var grantMapper = session.getMapper(ObjectGrantMapper.class);
        var persistence = new ApplicationPersistenceServiceImpl();
        ReflectionTestUtils.setField(persistence, "baseMapper", applicationMapper);
        var grants = new ObjectGrantPersistenceServiceImpl();
        ReflectionTestUtils.setField(grants, "baseMapper", grantMapper);
        var policy = new IdentityAuthorizationAdapter();
        ReflectionTestUtils.setField(policy, "objectGrantPersistenceService", grants);

        var firstPage = grantMapper.findGrantedObjectIdPage("actor", "APPLICATION", "recruitment.manage", null);
        assertThat(firstPage).hasSize(200).doesNotHaveDuplicates();
        var secondPage = grantMapper.findGrantedObjectIdPage("actor", "APPLICATION", "recruitment.manage", firstPage.getLast());
        assertThat(secondPage).hasSize(7).doesNotHaveDuplicates();
        assertThat(grantMapper.findGrantedObjectIdPage("actor", "APPLICATION", "recruitment.manage", secondPage.getLast())).isEmpty();

        for (var actor : List.of(actor("actor", "home", Set.of(), Set.of(), Set.of(), false, false),
            actor("actor", "unrelated", Set.of(), Set.of("managed"), Set.of(), false, false),
            actor("actor", "unrelated", Set.of("support"), Set.of(), Set.of(), false, false),
            actor("actor", "unrelated", Set.of(), Set.of(), Set.of("explicit"), false, false),
            actor("other", "unrelated", Set.of(), Set.of(), Set.of(), false, false),
            actor("actor", "home", Set.of(), Set.of(), Set.of(), true, false),
            actor("actor", "home", Set.of(), Set.of(), Set.of(), false, true))) {
          var authorization = mock(AuthorizationService.class);
          when(authorization.currentActor()).thenReturn(actor);
          when(authorization.grantedObjectIds(eq("recruitment.manage"), eq("APPLICATION"), any()))
              .thenAnswer(invocation -> grantMapper.findGrantedObjectIdPage(actor.getUserId(), "APPLICATION",
                  "recruitment.manage", invocation.getArgument(2)));
          var manager = new RecruitmentApplicationManager();
          ReflectionTestUtils.setField(manager, "persistence", persistence);
          ReflectionTestUtils.setField(manager, "authorization", authorization);
          for (String jd : List.of("jd-a", "jd-b", "")) {
            for (String version : List.of("v-a", "v-b", "")) {
              var selected = rows.stream().filter(row -> !row.created().isBefore(FROM) && row.created().isBefore(TO))
                  .filter(row -> jd.isEmpty() || jd.equals(row.jd()))
                  .filter(row -> version.isEmpty() || version.equals(row.jdVersion())).toList();
              var accessible = policy.accessibleObjectIds(actor, "recruitment.manage", selected.stream()
                  .map(row -> new AuthorizationTarget("APPLICATION", row.id(), row.owner(), row.department(), row.collaborators())).toList());
              Map<String, Long> expected = new LinkedHashMap<>();
              selected.stream().filter(row -> accessible.contains(row.id())).sorted((a, b) -> a.id().compareTo(b.id()))
                  .forEach(row -> expected.merge(row.stage(), 1L, Long::sum));
              assertThat(manager.stageCounts(FROM, TO, jd, version)).as("actor=%s jd=%s version=%s", actor, jd, version)
                  .containsExactlyElementsOf(expected.entrySet().stream()
                      .map(entry -> new ApplicationStageCountResponse(entry.getKey(), entry.getValue())).toList());
            }
          }
        }
        assertThat(persistence.countStages(new ApplicationStageCountQuery(FROM, TO, null, null, false,
            "actor", Set.of(), Set.of(), List.of()))).isEmpty();
        jdbc.update("INSERT INTO recruitment_application VALUES('nullable','IN_PROCESS',NULL,NULL,'jd-a','v-a',?)", Timestamp.from(FROM));
        assertThat(persistence.countStages(new ApplicationStageCountQuery(FROM, TO, null, null, false,
            "actor", Set.of(), Set.of(), List.of("nullable")))).singleElement()
            .satisfies(row -> assertThat(row.getCount()).isEqualTo(1));
      }
    }
  }

  private static List<Row> seed(JdbcTemplate jdbc) {
    var rows = new ArrayList<Row>();
    rows.add(new Row("a-owner", "IN_PROCESS", "actor", "hidden", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("b-managed", "ONBOARDED", "managed", "hidden", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("c-home", "IN_PROCESS", "stranger", "home", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("d-support", "ONBOARDED", "stranger", "support", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("e-explicit", "IN_PROCESS", "stranger", "explicit", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("f-collaborator", "ONBOARDED", "stranger", "hidden", Set.of("actor", "colleague"), "jd-a", "v-a", FROM));
    rows.add(new Row("h-hidden", "IN_PROCESS", "stranger", "hidden", Set.of(), "jd-a", "v-a", FROM));
    rows.add(new Row("i-before", "IN_PROCESS", "actor", "home", Set.of(), "jd-a", "v-a", FROM.minusSeconds(1)));
    rows.add(new Row("j-end", "IN_PROCESS", "actor", "home", Set.of(), "jd-a", "v-a", TO));
    rows.add(new Row("k-other-jd", "ONBOARDED", "actor", "home", Set.of(), "jd-b", "v-a", FROM));
    rows.add(new Row("l-other-version", "ONBOARDED", "actor", "home", Set.of(), "jd-a", "v-b", FROM));
    for (int index = 0; index < 205; index++) {
      String id = String.format("grant-%03d", index);
      rows.add(new Row(id, index % 2 == 0 ? "IN_PROCESS" : "ONBOARDED", "stranger", "hidden", Set.of(), "jd-a", "v-a", FROM));
      grant(jdbc, id, "actor", "APPLICATION", id, index % 2 == 0 ? "*" : "recruitment.manage");
    }
    // 精确与通配同时授予同一对象；以及内在范围与显式授权交叠，均只能计一次。
    grant(jdbc, "duplicate", "actor", "APPLICATION", "grant-000", "recruitment.manage");
    grant(jdbc, "overlap-owner", "actor", "APPLICATION", "a-owner", "*");
    grant(jdbc, "overlap-collaborator", "actor", "APPLICATION", "f-collaborator", "recruitment.manage");
    grant(jdbc, "wrong-type", "actor", "CANDIDATE", "h-hidden", "recruitment.manage");
    grant(jdbc, "wrong-permission", "actor", "APPLICATION", "h-hidden", "talent.read");
    grant(jdbc, "wrong-user", "other", "APPLICATION", "h-hidden", "recruitment.manage");
    for (var row : rows) {
      jdbc.update("INSERT INTO recruitment_application VALUES(?,?,?,?,?,?,?)", row.id(), row.stage(), row.owner(),
          row.department(), row.jd(), row.jdVersion(), Timestamp.from(row.created()));
      row.collaborators().forEach(user -> jdbc.update("INSERT INTO recruitment_application_collaborator VALUES(?,?)", row.id(), user));
    }
    return rows;
  }

  private static void grant(JdbcTemplate jdbc, String id, String user, String type, String objectId, String permission) {
    jdbc.update("INSERT INTO identity_object_grant VALUES(?,?,?,?,?)", id, user, type, objectId, permission);
  }

  private static CurrentActorSnapshot actor(String id, String home, Set<String> support, Set<String> managed,
      Set<String> explicit, boolean company, boolean admin) {
    return new CurrentActorSnapshot(id, "Synthetic", null, home, support, managed, explicit,
        Set.of("RECRUITER"), Set.of("recruitment.manage"), company, admin);
  }

  private record Row(String id, String stage, String owner, String department, Set<String> collaborators,
      String jd, String jdVersion, Instant created) {}
}

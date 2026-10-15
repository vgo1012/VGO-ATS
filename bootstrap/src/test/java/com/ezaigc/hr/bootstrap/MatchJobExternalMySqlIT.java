package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobResultRow;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 仅在显式外部测试环境的新建临时库执行生产 SQL，使用部署基线和合成人物。 */
@EnabledIfEnvironmentVariable(named = "HR_EXTERNAL_MYSQL_IT", matches = "true")
class MatchJobExternalMySqlIT {
  private static final String OWNER = "O".repeat(26);
  private static final String JD = "J".repeat(26);
  private static final String WEIGHT = "W".repeat(26);

  @Test void aggregatesAllRunsBeforePagingAndPreservesLatestSuccessfulSourceAndScope() throws Exception {
    String source = required("HR_DB_URL"), user = required("HR_DB_USER"), password = required("HR_DB_PASSWORD");
    URI endpoint = URI.create(source.substring("jdbc:".length()));
    assertThat(endpoint.getScheme()).isEqualTo("mysql");
    assertThat(endpoint.getRawUserInfo()).isNull();
    String schema = "hr_match_job_it_" + UUID.randomUUID().toString().replace("-", "");
    String url = "jdbc:mysql://" + endpoint.getRawAuthority() + "/" + schema
        + (endpoint.getRawQuery() == null ? "" : "?" + endpoint.getRawQuery());
    try (Connection admin = DriverManager.getConnection(source, user, password); Statement ddl = admin.createStatement()) {
      String sourceCatalog = admin.getCatalog();
      ddl.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
      try (Connection connection = DriverManager.getConnection(url, user, password)) {
        assertThat(connection.getCatalog()).isEqualTo(schema);
        assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("docs/sql/hr_ai_complete_schema.sql"))) root=root.getParent();
        assertThat(root).isNotNull();
        ScriptUtils.executeSqlScript(connection, new EncodedResource(
            new FileSystemResource(root.resolve("docs/sql/hr_ai_complete_schema.sql")), StandardCharsets.UTF_8));
        var dataSource = new SingleConnectionDataSource(connection, true);
        var jdbc = new JdbcTemplate(dataSource);
        // 仅临时库中的评分只读夹具省略不参与查询的外模块外键父数据，不改变部署表结构。
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        weight(jdbc, WEIGHT, 20);
        run(jdbc, 1, OWNER, WEIGHT, "SUCCEEDED", 52);
        for (int candidate=1; candidate<=52; candidate++) result(jdbc, 1, candidate, 70);
        run(jdbc, 2, OWNER, WEIGHT, "SUCCEEDED", 1); result(jdbc,2,53,80);
        run(jdbc, 3, OWNER, WEIGHT, "SUCCEEDED", 1); result(jdbc,3,54,80);

        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("match-job-it", new JdbcTransactionFactory(), dataSource));
        config.addMapper(MatchRunMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
          MatchRunMapper mapper=session.getMapper(MatchRunMapper.class);
          var query=query();
          // 即使授权范围无数据，MySQL 8.0 也会解析 CTE 的 JSON_TABLE 参数。
          query.setActorId("N".repeat(26)); query.setBasisRunId(id(1));
          assertThat(mapper.findJobBases(query)).isEmpty();
          assertThat(mapper.findJobResults(query)).isEmpty();
          assertThat(mapper.findJobResultsSummary(query).getTotal()).isZero();
          query.setActorId(OWNER);
          var jobs=mapper.findJobs(query);
          assertThat(jobs).hasSize(1);
          assertThat(jobs.getFirst().getRunCount()).isEqualTo(3);
          assertThat(jobs.getFirst().getEvaluationCount()).isEqualTo(54);
          assertThat(jobs.getFirst().getCandidateCount()).isEqualTo(54);
          assertThat(mapper.findJobBases(query)).hasSize(1);

          // 同一批由领导评分后，候选人当前归属人仅看到自己的结果和人数。
          String resumeOwner="N".repeat(26);
          jdbc.update("UPDATE talent_candidate SET owner_user_id=? WHERE id=?", resumeOwner, id(1));
          session.clearCache(); query.setActorId(resumeOwner); query.setBasisRunId(id(1));
          assertThat(mapper.findJobs(query).getFirst().getCandidateCount()).isEqualTo(1);
          assertThat(mapper.findJobs(query).getFirst().getEvaluationCount()).isEqualTo(1);
          assertThat(mapper.countVisibleJobBasis(query)).isEqualTo(1);
          assertThat(mapper.findJobBases(query)).hasSize(1);
          assertThat(mapper.findJobResultsSummary(query).getTotal()).isEqualTo(1);
          assertThat(mapper.findJobResults(query)).extracting(MatchJobResultRow::getCandidateId)
              .containsExactly(id(1));
          assertThat(mapper.findOwnedPendingResultCandidateIds(id(1), resumeOwner)).containsExactly(id(1));
          jdbc.update("UPDATE talent_candidate SET owner_user_id=? WHERE id=?", OWNER, id(1));
          session.clearCache(); query.setActorId(OWNER); query.setBasisRunId(null);

          // 新同值权重版本、反序的条件集合和不同偏好 ID 不拆散依据。
          String equivalent="E".repeat(26); weight(jdbc,equivalent,20);
          jdbc.update("UPDATE matching_weight_profile_version SET screening_settings_json=JSON_OBJECT('criteria',JSON_ARRAY(),'defaultSortMode','REFERENCE_SCORE') WHERE id=?", equivalent);
          run(jdbc,4,OWNER,equivalent,"SUCCEEDED",1); result(jdbc,4,1,95);
          jdbc.update("UPDATE matching_match_run SET selected_criterion_ids_json=JSON_ARRAY(?,?),personal_preference_version_id=? WHERE id=?",
              "B".repeat(26),"A".repeat(26),"P".repeat(26),id(4));
          run(jdbc,5,OWNER,WEIGHT,"FAILED",1); result(jdbc,5,1,99);
          run(jdbc,6,"H".repeat(26),WEIGHT,"SUCCEEDED",1); result(jdbc,6,55,100);
          session.clearCache();
          assertThat(mapper.findJobBases(query)).hasSize(1);
          var summary=mapper.findJobs(query).getFirst();
          assertThat(summary.getRunCount()).isEqualTo(5);
          assertThat(summary.getCandidateCount()).isEqualTo(54);
          assertThat(summary.getScoredCandidateCount()).isEqualTo(54);
          query.setBasisRunId(id(1)); query.setLimit(20);
          var all=new ArrayList<MatchJobResultRow>();
          for (int page=0;page<3;page++) {
            var rows=mapper.findJobResults(query); all.addAll(rows);
            query.setAfterRank(rows.getLast().getWorkspaceRank());
          }
          assertThat(all).hasSize(54);
          assertThat(all).extracting(MatchJobResultRow::getCandidateId).doesNotHaveDuplicates();
          assertThat(all.getFirst().getMatchRunId()).isEqualTo(id(4));
          assertThat(all.getFirst().getId()).isEqualTo(resultId(4,1));
          assertThat(all.getFirst().getBaseEvaluationId()).isEqualTo("X".repeat(26));
          assertThat(all.getFirst().getResumeVersionId()).isEqualTo(resultId(4,1));
          assertThat(all).noneMatch(row->row.getMatchRunId().equals(id(5))||row.getMatchRunId().equals(id(6)));

          // 汇总不跟随第三页游标：全部 54 人，跨批次同一人只计最新成功结果。
          var grades = mapper.findJobResultsSummary(query);
          assertThat(grades.getTotal()).isEqualTo(54);
          assertThat(grades.getStrong()).isEqualTo(1);
          assertThat(grades.getConsider()).isEqualTo(53);
          assertThat(grades.getMissing()).isZero();
          // 分数边界、缺少维度、禁用维度以及重复维度均沿用页面分级规则。
          jdbc.update("UPDATE matching_weighted_result SET reference_score=85 WHERE id=?", resultId(1,2));
          jdbc.update("UPDATE matching_weighted_result SET reference_score=69.99 WHERE id=?", resultId(1,3));
          jdbc.update("UPDATE matching_weighted_result SET result_json=JSON_OBJECT() WHERE id=?", resultId(1,4));
          jdbc.update("UPDATE matching_weighted_result SET result_json=JSON_SET(result_json,'$.dimensions[0].enabled',CAST('false' AS JSON)) WHERE id=?", resultId(1,5));
          jdbc.update("UPDATE matching_weighted_result SET result_json=JSON_SET(result_json,'$.dimensions[0].code','ROLE_EXPERIENCE') WHERE id=?", resultId(1,6));
          session.clearCache();
          grades = mapper.findJobResultsSummary(query);
          assertThat(grades.getTotal()).isEqualTo(54);
          assertThat(grades.getStrong()).isEqualTo(2);
          assertThat(grades.getConsider()).isEqualTo(48);
          assertThat(grades.getReject()).isEqualTo(1);
          assertThat(grades.getMissing()).isEqualTo(3);
          query.setCompanyScope(true);
          assertThat(mapper.findJobResultsSummary(query).getTotal()).isEqualTo(55);
          query.setCompanyScope(false);

          String revision=mapper.findJobRevision(query);
          String changed="C".repeat(26); weight(jdbc,changed,30);
          run(jdbc,7,OWNER,changed,"SUCCEEDED",1); result(jdbc,7,1,96);
          run(jdbc,8,OWNER,WEIGHT,"SUCCEEDED",1); result(jdbc,8,1,97);
          jdbc.update("UPDATE matching_match_run SET jd_fact_snapshot_id=? WHERE id=?","F".repeat(26),id(8));
          session.clearCache();
          assertThat(mapper.findJobBases(query)).hasSize(3);
          assertThat(mapper.findJobRevision(query)).isNotEqualTo(revision);
          query.setCompanyScope(true);
          assertThat(mapper.findJobs(query).getFirst().getCandidateCount()).isEqualTo(55);

          // 非空个人条件保留完整 JSON 对象；仅数组顺序不同仍可比较，内容变化则隔离。
          String age = "{\"kind\":\"AGE\",\"min\":20,\"max\":40}";
          String education = "{\"kind\":\"EDUCATION\",\"levels\":[\"BACHELOR\",\"MASTER\"]}";
          jdbc.update("UPDATE matching_weight_profile_version SET screening_settings_json=? WHERE id=?",
              "{\"criteria\":[" + age + "," + education + "]}", WEIGHT);
          jdbc.update("UPDATE matching_weight_profile_version SET screening_settings_json=? WHERE id=?",
              "{\"criteria\":[" + education + "," + age + "]}", equivalent);
          session.clearCache(); query.setCompanyScope(false); query.setAfterRank(0); query.setLimit(100);
          assertThat(mapper.findJobBases(query)).hasSize(3);
          assertThat(mapper.findJobResults(query)).hasSize(54)
              .first().extracting(MatchJobResultRow::getMatchRunId).isEqualTo(id(4));
          jdbc.update("UPDATE matching_weight_profile_version SET screening_settings_json=? WHERE id=?",
              "{\"criteria\":[" + education + "," + age.replace("40", "50") + "]}", equivalent);
          session.clearCache();
          assertThat(mapper.findJobBases(query)).hasSize(4);
          assertThat(mapper.findJobResults(query)).hasSize(54)
              .noneMatch(row -> row.getMatchRunId().equals(id(4)));
        }
      } finally {
        if (!schema.matches("hr_match_job_it_[0-9a-f]{32}")) throw new IllegalStateException("unexpected fixture schema");
        ddl.execute("DROP DATABASE `"+schema+"`");
        assertThat(admin.getCatalog()).isEqualTo(sourceCatalog);
      }
    }
  }

  private static MatchJobQuery query() {
    var q=new MatchJobQuery(); q.setActorId(OWNER); q.setManagedUserIds(Set.of()); q.setDepartmentIds(Set.of());
    q.setJdVersionId(JD); q.setLimit(50); return q;
  }
  private static void weight(JdbcTemplate jdbc,String id,int skills) {
    jdbc.update("INSERT INTO matching_weight_profile_version(id,profile_id,version_no,skills_weight,role_weight,years_weight,responsibility_weight,industry_project_weight) VALUES(?,?,1,?,20,20,20,?)",
        id,id,skills,40-skills);
  }
  private static void run(JdbcTemplate jdbc,int number,String owner,String weight,String status,int count) {
    jdbc.update("INSERT INTO matching_match_run(id,task_id,jd_version_id,jd_fact_snapshot_id,plan_version_id,criterion_plan_version_id,weight_profile_version_id,selection_snapshot_id,selected_criterion_ids_json,sort_mode,status,total_count,succeeded_count,owner_user_id,owner_department_id,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,JSON_ARRAY(?,?),'REFERENCE_SCORE',?,?,?,?,?,?,?)",
        id(number),id(number),JD,"D".repeat(26),"L".repeat(26),"K".repeat(26),weight,id(number),
        "A".repeat(26),"B".repeat(26),status,count,status.equals("SUCCEEDED")?count:0,owner,"T".repeat(26),
        "2026-09-09 00:00:"+String.format("%02d",number),"2026-09-09 00:00:"+String.format("%02d",number));
  }
  private static void result(JdbcTemplate jdbc,int run,int candidate,int score) {
    jdbc.update("INSERT IGNORE INTO talent_candidate(id,full_name,owner_user_id,owner_department_id,status) "
        + "SELECT ?,'合成候选人',owner_user_id,owner_department_id,'ACTIVE' FROM matching_match_run WHERE id=?",
        id(candidate), id(run));
    jdbc.update("INSERT INTO matching_candidate_selection_item(selection_snapshot_id,sequence_no,candidate_id,resume_version_id,resume_fact_snapshot_id) VALUES(?,?,?,?,?)",
        id(run),candidate,id(candidate),resultId(run,candidate),"F".repeat(26));
    jdbc.update("INSERT INTO matching_screening_admission(id,candidate_id,resume_version_id,jd_version_id,default_match_run_id,admitted_by_user_id,admitted_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
        resultId(run,candidate), id(candidate), resultId(run,candidate), JD, id(run), OWNER);
    jdbc.update("INSERT INTO matching_weighted_result(id,match_run_id,base_evaluation_id,candidate_id,resume_version_id,professional_score,bonus_score,reference_score,must_tier,stable_rank,result_json) VALUES(?,?,?,?,?,?,0,?,'PASS',?,CAST(? AS JSON))",
        resultId(run,candidate),id(run),"X".repeat(26),id(candidate),resultId(run,candidate),score,score,candidate, "{\"dimensions\":[" +
            Stream.of("CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS", "RESPONSIBILITY_MATCH", "INDUSTRY_AND_PROJECTS")
                .map(code -> "{\"code\":\"" + code + "\",\"enabled\":true,\"status\":\"KNOWN\"}")
                .collect(Collectors.joining(",")) + "]}");
  }
  private static String id(int value) { return String.format("%026d",value); }
  private static String resultId(int run,int candidate) { return String.format("%013d%013d",run,candidate); }
  private static String required(String name) {
    String value=System.getenv(name); assertThat(value).as("必须显式配置 %s",name).isNotBlank(); return value;
  }
}

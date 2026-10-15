package com.ezaigc.hr.intelligence.matching.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 真实 MySQL 回归另有环境门禁；这里验证生产 Mapper 动态分支实际绑定。 */
class MatchJobSqlBindingTest {
  @Test void storesTheCurrentAlgorithmVersionWithoutRelabelingHistoricalEvaluations() {
    var config = new MybatisConfiguration();
    config.addMapper(MatchRunMapper.class);
    String sql = config.getMappedStatement(MatchRunMapper.class.getName() + ".insertBase")
        .getBoundSql(null).getSql();
    assertThat(sql).contains("'" + AiScoringConstants.ALGORITHM_VERSION + "'")
        .doesNotContain("'deterministic-v1'", "'deterministic-v5-five-dimensions'");
  }

  @Test void bindsAllQueriesWithScopeBeforeAggregatesAndNoClientSqlInterpolation() {
    var config = new MybatisConfiguration(); config.addMapper(MatchRunMapper.class);
    var query = new MatchJobQuery(); query.setActorId("hr"); query.setJdVersionId("job");
    query.setManagedUserIds(Set.of("managed")); query.setDepartmentIds(Set.of("explicit"));
    query.setBeforeTime(Instant.EPOCH); query.setBeforeId("before"); query.setBasisRunId("basis"); query.setLimit(51);
    for (String method : List.of("findJobs", "findJobBases", "findJobResults", "findJobResultsSummary", "findJobRevision", "countVisibleJobBasis")) {
      var bound = config.getMappedStatement(MatchRunMapper.class.getName() + "." + method).getBoundSql(query);
      String sql = bound.getSql();
      assertThat(sql).contains("r.owner_user_id=?", "r.owner_department_id IN", "r.jd_version_id=?",
          "run_purpose='COMPANY_DEFAULT'", "matching_screening_admission",
          "run_purpose='CUSTOM'");
      assertThat(sql).contains("a.returned_at IS NULL", "recruitment_application application",
          "application.resume_version_id=a.resume_version_id");
      assertThat(sql).doesNotContain("application.current_stage", "application.active_slot");
      if (List.of("findJobs", "findJobBases", "findJobResults", "findJobResultsSummary").contains(method)) {
        assertThat(sql).contains("JOIN pending a ON a.candidate_id=");
      } else if (method.equals("findJobRevision")) {
        assertThat(sql).contains("SUM(a.version)", "MAX(a.update_time)");
      }
      assertThat(sql).doesNotContain("${", "hr", "explicit");
      assertThat(bound.getParameterMappings()).extracting(parameter -> parameter.getProperty())
          .contains("actorId", "jdVersionId");
    }
    String summarySql = config.getMappedStatement(MatchRunMapper.class.getName() + ".findJobResultsSummary")
        .getBoundSql(query).getSql();
    assertThat(summarySql).contains("candidate_position=1", "COUNT(DISTINCT dimension.code)", "reference_score >= 85", "reference_score >= 70")
        .doesNotContain("LIMIT", "workspace_rank", "afterRank");
    String resultSql = config.getMappedStatement(MatchRunMapper.class.getName() + ".findJobResults")
        .getBoundSql(query).getSql();
    assertThat(resultSql).contains("r.full_access=1 OR EXISTS", "b.full_access=1 OR a.candidate_owner_user_id=?")
        .contains("application.resume_version_id=a.resume_version_id");
    assertThat(config.getMappedStatement(MatchRunMapper.class.getName() + ".findOwnedPendingResultCandidateIds")
        .getBoundSql(Map.of("runId", "run", "actorId", "hr")).getSql())
        .contains("c.owner_user_id=?", "a.returned_at IS NULL", "application.resume_version_id=a.resume_version_id");
    query.setCompanyScope(true);
    assertThat(config.getMappedStatement(MatchRunMapper.class.getName()+".findJobs").getBoundSql(query).getSql())
        .contains("WHEN TRUE THEN 1").doesNotContain("r.owner_user_id=?");
  }
}

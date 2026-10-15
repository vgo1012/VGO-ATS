package com.ezaigc.hr.intelligence.matching.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class WeightedResultMapperContractTest {

  @Test
  void jobHistoryLookupIsExactSuccessfulAndCursorBounded() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(WeightedResultMapper.class);
    var bound = configuration.getMappedStatement(
        WeightedResultMapper.class.getName() + ".candidateIdsForJdVersion")
        .getBoundSql(Map.of("jdVersionId", "job", "afterId", "candidate", "limit", 200));
    assertThat(bound.getSql()).contains("r.jd_version_id=?", "w.candidate_id > ?",
        "w.stable_rank IS NOT NULL", "ORDER BY w.candidate_id LIMIT ?", "'SUCCEEDED','PARTIAL'");
    assertThat(bound.getParameterMappings()).extracting(value -> value.getProperty())
        .containsExactly("jdVersionId", "afterId", "limit");
  }

  @Test
  void sharedHistoryBindsOnlyVerifiedCandidatesWithoutOwnerOrActiveAdmissionRestriction() {
    var configuration = new MybatisConfiguration();
    configuration.addMapper(WeightedResultMapper.class);
    var statement = configuration.getMappedStatement(
        WeightedResultMapper.class.getName() + ".findCandidateMatchingStates");
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("candidateIds", List.of("shared"));
    parameters.put("ownerId", null);
    var shared = statement.getBoundSql(parameters);
    assertThat(shared.getSql()).contains("w.candidate_id IN", "r.status IN")
        .doesNotContain("r.owner_user_id", "matching_screening_admission", "returned_at");
    assertThat(shared.getParameterMappings()).extracting(value -> value.getProperty())
        .doesNotContain("ownerId");
    parameters.put("ownerId", "owner");
    assertThat(statement.getBoundSql(parameters).getSql()).contains("r.owner_user_id=?");
  }

  @Test
  void candidateProgressOnlyIncludesOwnedCompletedRuns() throws NoSuchMethodException {
    Method method =
        WeightedResultMapper.class.getDeclaredMethod(
            "findCandidateMatchingStates", List.class, String.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    assertThat(sql)
        .contains(
            "r.owner_user_id=#{ownerId}",
            "r.status IN ('"
                + MatchRunStatus.SUCCEEDED.getCode()
                + "','"
                + MatchRunStatus.PARTIAL.getCode()
                + "')",
            "w.resume_version_id",
            "w.candidate_id IN")
        .doesNotContain("${");
  }

  @Test
  void rankUpdateIsOneRunScopedAndFullyParameterizedStatement() throws NoSuchMethodException {
    Method method =
        WeightedResultMapper.class.getDeclaredMethod(
            "updateRanks", String.class, List.class, String.class, Instant.class);
    String sql = String.join(" ", method.getAnnotation(Update.class).value());

    assertThat(sql)
        .contains("CASE id", "match_run_id = #{runId}", "#{result.id}", "#{result.stableRank}")
        .contains("update_id = #{updateId}", "version = version + 1")
        .doesNotContain("${");
  }
}

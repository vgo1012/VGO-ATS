package com.ezaigc.hr.intelligence.matching.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import java.lang.reflect.Method;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AiProfessionalAssessmentMapperContractTest {

  @Test
  void cacheReadUsesExplicitColumnsAndIgnoresDeletedRows() throws NoSuchMethodException {
    Method method =
        AiProfessionalAssessmentMapper.class.getDeclaredMethod("findByCacheKey", String.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());

    assertThat(sql)
        .contains("WHERE cache_key=#{cacheKey} AND deleted=FALSE", "result_json", "claim_token")
        .doesNotContain("SELECT *", "${");
  }

  @Test
  void claimPreservesFrozenIdentityAndOnlyTakesFailedOrExpiredRows()
      throws NoSuchMethodException {
    Method method =
        AiProfessionalAssessmentMapper.class.getDeclaredMethod(
            "claim",
            AiProfessionalAssessment.class,
            Instant.class,
            String.class,
            String.class,
            String.class);
    String sql = String.join(" ", method.getAnnotation(Insert.class).value());

    assertThat(sql)
        .contains(
            "ON DUPLICATE KEY UPDATE",
            "status=#{failedStatus} OR",
            "status=#{processingStatus} AND lease_until<=#{now}",
            "claim_token=IF",
            "lease_until=IF(claim_token=VALUES(claim_token)",
            "status=IF(claim_token=VALUES(claim_token),#{processingStatus},status)",
            "evaluated_on=IF(claim_token=VALUES(claim_token),VALUES(evaluated_on),evaluated_on)")
        .doesNotContain(
            "jd_fact_snapshot_id=VALUES",
            "resume_fact_snapshot_id=VALUES",
            "prompt_version=VALUES",
            "schema_version=VALUES",
            "result_json=VALUES",
            "${");
    assertThat(sql.indexOf("claim_token=IF")).isLessThan(sql.indexOf("lease_until=IF"));
    assertThat(sql.indexOf("lease_until=IF")).isLessThan(sql.indexOf("status=IF"));
  }

  @Test
  void terminalWritesRequireCurrentUnexpiredClaimAndNeverUpdateSuccessAgain()
      throws NoSuchMethodException {
    Method complete =
        AiProfessionalAssessmentMapper.class.getDeclaredMethod(
            "complete",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            int.class,
            int.class,
            long.class,
            Instant.class,
            String.class,
            String.class,
            String.class);
    Method fail =
        AiProfessionalAssessmentMapper.class.getDeclaredMethod(
            "fail",
            String.class,
            String.class,
            String.class,
            Instant.class,
            String.class,
            String.class,
            String.class);
    String completeSql = String.join(" ", complete.getAnnotation(Update.class).value());
    String failSql = String.join(" ", fail.getAnnotation(Update.class).value());

    assertThat(completeSql)
        .contains(
            "status=#{processingStatus}",
            "claim_token=#{claimToken}",
            "lease_until>#{now}",
            "result_json=CAST(#{resultJson} AS JSON)")
        .doesNotContain("${");
    assertThat(failSql)
        .contains("status=#{processingStatus}", "claim_token=#{claimToken}", "lease_until>#{now}")
        .doesNotContain("${");
  }
}

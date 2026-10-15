package com.ezaigc.hr.recruiting.recruitment.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ApplicationMapperWorkbenchScopeTest {

  @Test
  void lifecycleCompletesRecruitmentWhenOnboardingStartsBeforePagination() throws NoSuchMethodException {
    var method = ApplicationMapper.class.getMethod("findApplications", String.class, String.class,
        String.class, String.class, String.class, String.class, Instant.class, Instant.class,
        String.class, boolean.class, Instant.class, String.class, int.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    assertThat(sql).contains("#{lifecycle} = 'ALL'",
        "#{stage}='ONBOARDED' AND EXISTS",
        "#{lifecycle} = 'ACTIVE' AND terminal_at IS NULL",
        "NOT EXISTS (SELECT 1 FROM recruitment_onboarding_process active_op",
        "#{lifecycle} = 'ARCHIVED' AND (terminal_at IS NOT NULL OR EXISTS",
        "active_op.status IN ('IN_PROGRESS','ONBOARDED')",
        "archived_op.status IN ('IN_PROGRESS','ONBOARDED')");
    assertThat(sql.indexOf("#{lifecycle}")).isLessThan(sql.indexOf("LIMIT #{limit}"));
    assertThat(sql).startsWith("SELECT ");
    assertThat(sql).contains("update_time >= #{from}", "update_time < #{to}",
        "its.status IN ('PENDING','IN_PROGRESS')", "current_stage='IN_PROCESS' AND terminal_at IS NULL",
        "earlier.sequence_no < its.sequence_no", "earlier.id < its.id",
        "its.step_code='PRE_SUBMISSION_INTERNAL_INTERVIEW'", "recruitment_type='INTERNAL'",
        "#{interviewType}='CLIENT'", "recruitment_type='EXTERNAL'",
        "#{interviewType}='CLIENT_AI' AND its.step_code='CLIENT_AI_INTERVIEW'");
    assertThat(sql).contains("op.status IN ('IN_PROGRESS','ONBOARDED')");
    assertThat(sql.indexOf("#{from}")).isLessThan(sql.indexOf("LIMIT #{limit}"));
    assertThat(sql.indexOf("#{interviewType}")).isLessThan(sql.indexOf("LIMIT #{limit}"));
  }

  @Test
  void workbenchVisibilityIsFilteredBeforeDatabaseLimit() throws NoSuchMethodException {
    var method =
        ApplicationMapper.class.getMethod(
            "findWorkItems", String.class, boolean.class, Set.class, int.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());

    assertThat(sql)
        .contains("#{scopeAll} = TRUE")
        .contains("s.owner_user_id = #{actorId}")
        .contains("collection=\"managedUserIds\"");
    assertThat(sql.indexOf("#{scopeAll} = TRUE")).isLessThan(sql.indexOf("LIMIT #{limit}"));
    assertThat(Arrays.stream(method.getParameters()).mapToInt(parameter -> parameter.getAnnotations().length))
        .allMatch(annotationCount -> annotationCount > 0);
  }

  @Test
  void myTaskCandidateLookupFiltersOwnerAndActiveStatusWithoutLimit()
      throws NoSuchMethodException {
    var method =
        ApplicationMapper.class.getMethod(
            "findMyTaskCandidateIds", String.class, List.class, String.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());

    assertThat(sql)
        .contains("SELECT DISTINCT a.candidate_id")
        .contains("a.jd_id = #{jdId}", "a.terminal_at IS NULL")
        .contains("s.owner_user_id = #{actorId}")
        .contains("s.status IN ('PENDING','IN_PROGRESS')")
        .contains("collection=\"candidateIds\"")
        .doesNotContain("LIMIT");
    assertThat(Arrays.stream(method.getParameters()).mapToInt(parameter -> parameter.getAnnotations().length))
        .allMatch(annotationCount -> annotationCount > 0);
  }
}

package com.ezaigc.hr.intelligence.matching.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.ezaigc.hr.intelligence.matching.domain.entity.ScreeningAdmission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScreeningAdmissionMapperContractTest {
  @Test
  void selectionPagesOnlyExactJobAdmissionsBeforeApplyingTheLimit() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ScreeningAdmissionMapper.class);
    var parameters = Map.of("jdVersionId", "jd", "query", "小王", "actorId", "actor",
        "scopeAll", false, "managedUserIds", List.of("managed"),
        "visibleDepartmentIds", List.of("department"), "beforeTime", "2026-09-11T00:00:00Z",
        "beforeId", "admission", "limit", 21);

    String sql = configuration.getMappedStatement(
        ScreeningAdmissionMapper.class.getName() + ".findSelectionCandidates")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains("a.jd_version_id=?", "a.returned_at is null",
        "r.run_purpose='company_default'", "r.personal_preference_version_id is null",
        "r.weight_profile_version_id='00000000000000000000000022'",
        "c.full_name as candidate_name", "c.full_name like concat('%',?,'%')",
        "not exists (select 1 from recruitment_application application",
        "application.resume_version_id=a.resume_version_id",
        "application.current_stage not in ('terminated','onboarded')", "r.owner_user_id=?",
        "c.owner_user_id=?",
        "r.owner_user_id in", "r.owner_department_id in",
        "a.admitted_at < ?", "a.id < ?", "order by a.admitted_at desc,a.id desc limit ?");
    assertThat(sql).doesNotContain("personal_pool", "limit 1001", "group by", "primary_email", "primary_phone");
  }

  @Test
  void readyQueryPrefersTheLatestExplicitRescoreAndFreezesAnAdmittedRun() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ScreeningAdmissionMapper.class);
    var parameters = Map.of(
        "candidateIds", List.of("candidate"),
        "actorId", "actor",
        "scopeAll", false,
        "managedUserIds", List.of("managed"),
        "visibleDepartmentIds", List.of("department"),
        "admittedOnly", false);

    String sql = configuration.getMappedStatement(
        ScreeningAdmissionMapper.class.getName() + ".findScreeningCandidates")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains(
        "r.run_purpose='company_default'",
        "r.weight_profile_version_id='00000000000000000000000022'",
        "r.personal_preference_version_id is null",
        "i.resume_version_id=w.resume_version_id and i.score_run_id=r.id",
        "i.status='completed' and i.score_status='succeeded'",
        "b.jd_version_id=r.jd_version_id",
        "left join talent_resume_jd_score_binding score_binding",
        "score_binding.default_match_run_id=r.id",
        "evaluation.id=w.base_evaluation_id",
        "partition by w.candidate_id,w.resume_version_id,r.jd_version_id order by case when score_binding.id is null then 1 else 0 end asc, score_binding.selected_at desc,r.create_time asc,r.id asc",
        "or score_binding.id is not null)) select c.candidate_id",
        "a.id is null and c.position_no=1) or c.default_match_run_id=a.default_match_run_id",
        "c.default_match_run_id=a.default_match_run_id",
        "c.owner_user_id=?",
        "join talent_candidate candidate on candidate.id=c.candidate_id",
        "a.id is not null and candidate.owner_user_id=?",
        "c.owner_user_id in",
        "c.owner_department_id in",
        "c.candidate_id in");
    assertThat(sql).contains("a.returned_at is null", "not exists (select 1 from recruitment_application application",
        "application.resume_version_id=c.resume_version_id",
        "application.current_stage not in ('terminated','onboarded')", "a.version as admission_version");
    assertThat(sql).doesNotContain("application.active_slot", "current_fact", "talent_resume_fact_snapshot");
  }

  @Test
  void companyWideScopeAndAdmittedOnlyUseTheSharedVisibilityProjection() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ScreeningAdmissionMapper.class);
    var parameters = Map.of(
        "candidateIds", List.of(),
        "actorId", "actor",
        "scopeAll", true,
        "managedUserIds", List.of(),
        "visibleDepartmentIds", List.of(),
        "admittedOnly", true);

    String sql = configuration.getMappedStatement(
        ScreeningAdmissionMapper.class.getName() + ".findScreeningCandidates")
        .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains("?=true", "and a.id is not null")
        .doesNotContain("c.candidate_id in");
  }

  @Test
  void admissionWriteUsesDatabaseConflictArbitrationForTheExactTriple() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ScreeningAdmissionMapper.class);
    String sql = configuration.getMappedStatement(
        ScreeningAdmissionMapper.class.getName() + ".insertBatchIgnore")
        .getBoundSql(Map.of("items", List.of(new ScreeningAdmission())))
        .getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains("insert ignore into matching_screening_admission",
        "candidate_id,resume_version_id,jd_version_id,default_match_run_id");
  }

  @Test
  void completedFlowEndsOnlyActiveAdmissionsForTheExactResume() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.addMapper(ScreeningAdmissionMapper.class);
    String sql = configuration.getMappedStatement(
        ScreeningAdmissionMapper.class.getName() + ".completeActive")
        .getBoundSql(Map.of(
            "candidateId", "candidate",
            "resumeVersionId", "resume",
            "actorId", "actor",
            "now", "2026-09-21T00:00:00Z"))
        .getSql().replaceAll("\\s+", " ").toLowerCase();

    assertThat(sql).contains(
        "update matching_screening_admission set returned_at=?",
        "version=version+1",
        "candidate_id=?",
        "resume_version_id=?",
        "returned_at is null");
  }
}

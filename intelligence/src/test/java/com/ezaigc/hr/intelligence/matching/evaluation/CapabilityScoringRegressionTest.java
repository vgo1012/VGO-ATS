package com.ezaigc.hr.intelligence.matching.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.intelligence.matching.domain.dto.MatchEvaluationDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.DeterministicMatchingEngine;
import com.ezaigc.hr.intelligence.matching.domain.service.ControlledCriteriaEvaluator;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Skill;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class CapabilityScoringRegressionTest {
  @Test
  void missingFactsAreUnknownAcrossAllFiveParticipatingDimensions() {
    var result = evaluate(jd(List.of(skill("设备巡检", true)), List.of("设备巡检"),
        List.of("园区运维")), resume(List.of(), List.of()));
    assertThat(result.getDimensions()).allSatisfy(dimension -> {
      assertThat(dimension.isEnabled()).isTrue();
      assertThat(dimension.getConfiguredWeight()).isPositive();
      assertThat(dimension.getStatus()).isEqualTo("UNKNOWN");
    });
  }

  @Test
  void evaluatesAllFiveDimensionsWithoutInventingJdThresholds() {
    var source = resume(List.of("设备巡检"), List.of(new ResumeFactSnapshotData.Project(
        "p", "园区运维", "运维员", null, null, "负责设备巡检", List.of(), List.of())));
    source.setWorkExperiences(List.of(new ResumeFactSnapshotData.Work("w", "示例园区", "运维员",
        LocalDate.of(2023, 9, 20), LocalDate.of(2026, 9, 20),
        "负责设备巡检", List.of(), List.of())));
    var result = evaluate(jd(List.of(skill("设备巡检", true)), List.of(), List.of()), source);
    assertThat(result.getDimensions()).allSatisfy(dimension -> {
      assertThat(dimension.isEnabled()).isTrue();
      assertThat(dimension.getStatus()).isEqualTo("KNOWN");
      assertThat(dimension.getScore()).isPositive();
    });
    assertThat(result.getDimensions().get(2).getExplanation()).contains("评价参照", "不是 JD 门槛");
  }

  @Test
  void retainsProvenYearsWhenAnotherRelatedJobHasMissingDates() {
    var source = resume(List.of(), List.of());
    source.setWorkExperiences(List.of(
        new ResumeFactSnapshotData.Work("w1", "示例园区", "运维员", LocalDate.of(2023, 9, 20),
            LocalDate.of(2026, 9, 20), "负责设备巡检", List.of(), List.of()),
        new ResumeFactSnapshotData.Work("w2", "另一园区", "运维员", null, null,
            "负责设备巡检", List.of(), List.of())));
    var result = evaluate(jd(List.of(skill("设备巡检", true)), List.of(), List.of()), source);
    assertThat(result.getDimensions().get(2).getScore()).isEqualByComparingTo("100.00");
    assertThat(result.getDimensions().get(2).getExplanation()).contains("日期不全", "下界");
  }

  @Test
  void unrelatedLongEmploymentCannotBecomeRelevantYears() {
    var source = resume(List.of("Java"), List.of());
    source.setWorkExperiences(List.of(new ResumeFactSnapshotData.Work("w", "示例公司", "厨师",
        LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1),
        "负责餐饮烹饪", List.of(), List.of())));
    var result = evaluate(jd(List.of(skill("Java", true)), List.of(), List.of()), source);
    assertThat(result.getDimensions().get(2).getStatus()).isEqualTo("UNKNOWN");
  }

  @Test
  void scoresEachParsedSkillIndependentlyWithoutEvidenceGroups() {
    var jd = jd(List.of(skill("Python", true), skill("PyTorch", true), skill("TensorFlow", true)),
        List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of("Python", "PyTorch"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("66.67");
    assertThat(evaluate(jd, resume(List.of("PyTorch"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("33.33");
  }

  @Test
  void keepsParsedRequirementsSeparate() {
    var jd = jd(List.of(skill("Python", true), skill("Docker", true)), List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of("Python"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("50");
  }

  @Test
  void parsedSkillsDoNotRegainHiddenAlternativeGroups() {
    var jd = jd(List.of(skill("Python", true), skill("Java", true), skill("Go", true)),
        List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of("Python"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("33.33");
    assertThat(evaluate(jd, resume(List.of("Java"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("33.33");
  }

  @Test
  void shortTechnologyNamesCannotJoinAnAlternativeBySubstring() {
    var jd = jd(List.of(skill("C++", true), skill("Python", true), skill("C", true)),
        List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of("Python"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("33.33");
  }

  @Test
  void explicitAllQualifierOverridesSlashNotation() {
    var jd = jd(List.of(skill("PyTorch", true), skill("TensorFlow", true)),
        List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of("PyTorch"), List.of())).getDimensions().get(0).getScore())
        .isEqualByComparingTo("50");
  }

  @Test
  void projectChoiceCannotConsumeIndependentScenarioInSameSource() {
    var jd = jd(List.of(), List.of(), List.of("支付", "智能问答", "图像识别"));
    var project = new ResumeFactSnapshotData.Project("p", "支付系统", null, null, null,
        "开发支付系统", List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of(), List.of(project))).getDimensions().get(4).getScore())
        .isEqualByComparingTo("33.33");
  }

  @Test
  void optionalSkillsCanOnlyImproveCoreCoverageAndNeverChangeAdmissionTier() {
    var jd = jd(List.of(skill("Python", true), skill("Docker", true), skill("Kubernetes", false)),
        List.of(), List.of());
    var base = evaluate(jd, resume(List.of("Python"), List.of()));
    var preferred = evaluate(jd, resume(List.of("Python", "Kubernetes"), List.of()));
    var complete = evaluate(jd, resume(List.of("Python", "Docker"), List.of()));
    assertThat(base.getDimensions().get(0).getScore()).isEqualByComparingTo("50");
    assertThat(preferred.getDimensions().get(0).getScore()).isEqualByComparingTo("70");
    assertThat(complete.getDimensions().get(0).getScore()).isEqualByComparingTo("100");
    assertThat(base.getMustTier()).isEqualTo("PASS");
    assertThat(base.getDimensions().getFirst().getExplanation()).doesNotContain("必须");
  }

  @Test
  void recognizesCapabilitiesDemonstratedInProjectNarrativeAndAchievements() {
    var jd = jd(List.of(skill("Prompt工程", true), skill("向量数据库", true)),
        List.of("模型微调", "模型量化", "服务化部署"), List.of());
    var project = new ResumeFactSnapshotData.Project("p", "知识助手", null, null, null,
        "使用LoRA微调模型，采用4bit量化；设计System Prompt，使用Milvus构建知识库。",
        List.of(), List.of("通过FastAPI完成服务化部署"));
    var result = evaluate(jd, resume(List.of(), List.of(project)));
    assertThat(result.getDimensions().getFirst().getScore()).isEqualByComparingTo("100");
    assertThat(result.getDimensions().get(3).getScore()).isEqualByComparingTo("100");
  }

  @Test
  void doesNotTurnAListOfTechnologiesIntoCompletedResponsibilities() {
    var jd = jd(List.of(), List.of("模型微调", "模型服务部署"), List.of());
    var project = new ResumeFactSnapshotData.Project("p", "课程作业", null, null, null,
        "计划学习LoRA微调，未进行模型服务部署", List.of("Python"), List.of());
    assertThat(evaluate(jd, resume(List.of("Python"), List.of(project))).getDimensions().get(3).getScore())
        .isEqualByComparingTo("0");
  }

  @Test
  void readingAboutTechniqueDoesNotProveImplementationExperience() {
    var jd = jd(List.of(), List.of("模型微调"), List.of());
    var project = new ResumeFactSnapshotData.Project("p", "技术阅读", null, null, null,
        "阅读LoRA论文，了解微调原理", List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of(), List.of(project))).getDimensions().get(3).getScore())
        .isEqualByComparingTo("0");
  }

  @Test
  void scoresParsedProjectScenariosIndependently() {
    var jd = jd(List.of(), List.of(), List.of("智能问答", "图像识别"));
    var project = new ResumeFactSnapshotData.Project("p", "智能问答助手", null, null, null,
        "开发智能问答助手", List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of(), List.of(project))).getDimensions().get(4).getScore())
        .isEqualByComparingTo("50");
  }

  @Test
  void noExperienceThresholdKeepsUnknownDimensionsParticipating() {
    var jd = jd(List.of(skill("Python", true)), List.of(), List.of());
    var result = evaluate(jd, resume(List.of("Python"), List.of()));
    assertThat(result.getDimensions().get(1).isEnabled()).isTrue();
    assertThat(result.getDimensions().get(2).isEnabled()).isTrue();
    assertThat(result.getDimensions().get(2).getExplanation()).contains("评价参照", "不是 JD 门槛");
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("20.00");
    assertThat(result.getDimensions().get(0).getScore()).isEqualByComparingTo("100");
  }

  @Test
  void doesNotMergeIndependentProjectRequirementsFromOneSentence() {
    var jd = jd(List.of(), List.of(), List.of("支付", "清算"));
    var project = new ResumeFactSnapshotData.Project("p", "支付系统", null, null, null,
        "开发支付系统", List.of(), List.of());
    assertThat(evaluate(jd, resume(List.of(), List.of(project))).getDimensions().get(4).getScore())
        .isEqualByComparingTo("50");
  }

  private static Skill skill(String name, boolean core) {
    return new Skill(name, name, core, List.of());
  }

  private static JdFactSnapshotData jd(List<Skill> skills, List<String> responsibilities,
      List<String> projects) {
    return new JdFactSnapshotData("jd-fact", "jd-version", 1,
        new JdFactSnapshotData.ProfessionalFacts(skills, List.of(), null, null,
            responsibilities, List.of(), projects),
        new JdFactSnapshotData.ControlledFacts(null, null, null, List.of(), List.of(), List.of()),
        new JdFactSnapshotData.FulfillmentFacts(List.of(), null, null, null, null));
  }

  private static ResumeFactSnapshotData resume(List<String> skills,
      List<ResumeFactSnapshotData.Project> projects) {
    return new ResumeFactSnapshotData("resume-fact", "resume-version", "candidate", 1,
        new ResumeFactSnapshotData.CandidateFacts("匿名", null, null, null, null, null, null, null),
        List.of(), projects, List.of(), skills.stream().map(name ->
            new ResumeFactSnapshotData.Skill(name, name, null, null)).toList(), List.of());
  }

  private static MatchEvaluationDTO evaluate(JdFactSnapshotData jd, ResumeFactSnapshotData resume) {
    var engine = new DeterministicMatchingEngine();
    var controlled = new ControlledCriteriaEvaluator();
    ReflectionTestUtils.setField(controlled, "json", JsonMapper.builder().build());
    ReflectionTestUtils.setField(engine, "controlledCriteria", controlled);
    ReflectionTestUtils.setField(engine, "clock",
        Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));
    var plan = new MatchPlanVersion();
    plan.setSkillsEnabled(true);
    plan.setRoleEnabled(true);
    plan.setYearsEnabled(true);
    plan.setResponsibilityEnabled(true);
    plan.setIndustryProjectEnabled(true);
    return engine.evaluate(jd, resume, plan,
        new WeightProfileVersion("weights", "profile", 1, 20, 20, 20, 20, 20), List.of(), Set.of(), 0);
  }
}

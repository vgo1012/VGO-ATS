package com.ezaigc.hr.intelligence.matching.evaluation;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHTS;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_HIGHLIGHT_TEXT_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.DeterministicMatchingEngine;
import com.ezaigc.hr.intelligence.matching.domain.service.ControlledCriteriaEvaluator;
import com.ezaigc.hr.intelligence.matching.domain.service.PersonalScreeningSettings;
import com.ezaigc.hr.intelligence.matching.domain.service.ScoringConfigurationValidator;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.ControlledFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.FulfillmentFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Language;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.ProfessionalFacts;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Role;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData.Skill;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchCriterionResultDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchDimensionResultDTO;

class DeterministicMatchingEngineTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
  private final DeterministicMatchingEngine engine = engine();

  @ParameterizedTest
  @CsvSource(value = {
      "NULL|Java|false", "Java|NULL|false", "''|Java|false", "Java|''|false",
      "'   '|Java|false", "Java|'   '|false", "...|...|false",
      "JAVA|java|true", "负责 Java 开发|Java|true", "JavaScript|Java|false",
      "MongoDB|Go|false", "Java-17|Java|false", "Java/Go|Java|true",
      ".NET|.net|true", "使用 .NET 开发|.NET|true", "C++开发|C++|true",
      "C++17|C++|false", "C#工程师|C#|true", "XML|ML|false",
      "SQL/JSON|SQL|true", "SQL.JSON|SQL|false", "高级Java工程师|Java工程师|true",
      "高级 Java 工程师|Java工程师|true", "数据-分析|数据分析|true",
      "不相关|数据分析|false", "İstanbul|istanbul|true", "I|i|true"
  }, delimiter = '|', nullValues = "NULL")
  void preservesTextBoundariesForRoleHighlightsAndControlledCertificates(
      String actual, String expected, boolean matched) {
    var source = jd();
    var roleJd = new JdFactSnapshotData(
        source.getSnapshotId(), source.getJdVersionId(), source.getSnapshotVersion(),
        new ProfessionalFacts(List.of(), List.of(new Role("role", expected, null)),
            null, null, List.of(), List.of(), List.of()),
        source.getControlled(), source.getFulfillment());
    var facts = new ResumeFactSnapshotData(
        "rf", "rv", "candidate", 1,
        new ResumeFactSnapshotData.CandidateFacts(
            "匿名", null, null, actual, null, null, null, null),
        List.of(), List.of(), List.of(), List.of(),
        List.of(new ResumeFactSnapshotData.SimpleFact(
            "CREDENTIAL", "credential", actual, null, null, null, null)));
    var json = JsonMapper.builder().build();
    String expectedJson = json.writeValueAsString(json.createObjectNode().put("value", expected));
    var conditions = Stream.of("IN", "EQ", "CONTAINS").map(operator -> {
      var item = criterion(operator, "CERTIFICATE", "PREFER", expectedJson, 10, false);
      item.setOperator(operator);
      return item;
    }).toList();

    var result = engine.evaluate(roleJd, facts, plan(true, true, true, true, true),
        weights(), conditions, Set.of(), 30);

    assertThat(result.getDimensions().get(1).getStatus()).isEqualTo(matched ? "KNOWN" : "UNKNOWN");
    assertThat(result.getDimensions().get(1).getScore()).isEqualByComparingTo(matched ? "100" : "0");
    assertThat(result.getDimensions().get(1).getHighlights())
        .containsExactlyElementsOf(matched ? List.of(expected + " ← " + actual) : List.of());
    assertThat(result.getCriteria()).hasSize(3).allSatisfy(item -> {
      assertThat(item.getStatus()).isEqualTo(matched ? "PASS" : "UNKNOWN");
      assertThat(item.getAwardedBonus()).isEqualTo(matched ? 10 : 0);
    });
    assertThat(result.getBonusScore()).isEqualTo(matched ? 30 : 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"in", " in ", "contains"})
  @ResourceLock(Resources.LOCALE)
  void evaluatesOperatorAliasesAcceptedAndFrozenByPersonalSettings(String operator) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.ENGLISH);
      var draft = new ControlledCriterionDraft(ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.PREFER,
          operator, "{\"values\":[\"英语\"]}", 10, "岗位要求", "EXPLICIT_FACT_ONLY", false);
      var personal = weights();
      personal.setScreeningSettingsJson(PersonalScreeningSettings.validateAndSerialize(
          List.of(draft), "BONUS_SCORE", new ScoringConfigurationValidator()));
      assertThat(PersonalScreeningSettings.merge(List.of(), personal).getFirst().getOperator()).isEqualTo(operator);
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      var result = engine.evaluate(jd(), resume(), plan(true, true, true, true, true), personal, List.of(), Set.of(), 0);

      assertThat(result.getCriteria()).singleElement().satisfies(item -> {
        assertThat(item.getStatus()).isEqualTo("PASS");
        assertThat(item.getAwardedBonus()).isEqualTo(10);
      });
      assertThat(result.getBonusScore()).isEqualTo(10);
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  private static DeterministicMatchingEngine engine() {
    DeterministicMatchingEngine engine = new DeterministicMatchingEngine();
    var controlled = new ControlledCriteriaEvaluator();
    ReflectionTestUtils.setField(controlled, "json", JsonMapper.builder().build());
    ReflectionTestUtils.setField(engine, "controlledCriteria", controlled);
    ReflectionTestUtils.setField(engine, "clock", CLOCK);
    return engine;
  }

  @Test
  void evaluatesPersonalMustAndBonusFromFrozenWeightVersion() {
    var personal = weights();
    personal.setScreeningSettingsJson("""
        {"defaultSortMode":"BONUS_SCORE","criteria":[
          {"code":"EDUCATION","level":"MUST","operator":"IN","expectedValueJson":"{\\\"values\\\":[\\\"博士\\\"]}","bonusPoints":0,"reason":"岗位要求","evidencePolicy":"EXPLICIT_FACT_ONLY","selectableByHr":false},
          {"code":"LANGUAGE","level":"PREFER","operator":"IN","expectedValueJson":"{\\\"values\\\":[\\\"英语\\\"]}","bonusPoints":10,"reason":"岗位要求","evidencePolicy":"EXPLICIT_FACT_ONLY","selectableByHr":false}
        ]}
        """);
    var result = engine.evaluate(jd(), resume(), plan(true, true, true, true, true), personal, List.of(), Set.of(), 0);
    assertThat(result.getMustTier()).isEqualTo("FAIL");
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("83.33");
    assertThat(result.getCriteria()).hasSize(2);
    assertThat(result.getSelectedBonusCap()).isEqualTo(10);
    var legacy = engine.evaluate(jd(), resume(), plan(true, true, true, true, true), weights(), List.of(), Set.of(), 0);
    assertThat(legacy.getMustTier()).isEqualTo("PASS");
    assertThat(legacy.getCriteria()).isEmpty();
  }

  @Test
  void appliesFixedFiveDimensionWeightsAndHalfUpWithoutAiScoring() {
    var result =
        engine.evaluate(
            jd(), resume(), plan(true, true, true, true, true), weights(), List.of(), Set.of(), 0);
    assertThat(result.getDimensions())
        .extracting(MatchDimensionResultDTO::getConfiguredWeight)
        .containsExactly(20, 20, 20, 20, 20);
    assertThat(result.getDimensions())
        .extracting(MatchDimensionResultDTO::getScore)
        .containsExactly(
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            new BigDecimal("50.00"),
            new BigDecimal("66.67"));
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("83.33");
    assertThat(result.getReferenceScore()).isEqualByComparingTo("83.33");
  }

  @Test
  void recalculatesTheSameFactsUsingPositivePersonalWeights() {
    var company = engine.evaluate(
        jd(), resume(), plan(true, true, true, true, true), weights(), List.of(), Set.of(), 0);
    var personalWeights = new WeightProfileVersion("personal-v2", "personal", 2, 46, 18, 21, 14, 1);

    var personal = engine.evaluate(
        jd(), resume(), plan(true, true, true, true, true), personalWeights, List.of(), Set.of(), 0);

    assertThat(personal.getDimensions())
        .extracting(MatchDimensionResultDTO::getConfiguredWeight)
        .containsExactly(46, 18, 21, 14, 1);
    assertThat(personal.getDimensions())
        .extracting(MatchDimensionResultDTO::getScore)
        .containsExactlyElementsOf(company.getDimensions().stream()
            .map(MatchDimensionResultDTO::getScore).toList());
    // 100 × 46% + 100 × 18% + 100 × 21% + 50 × 14% + 66.67 × 1%。
    assertThat(personal.getProfessionalScore()).isEqualByComparingTo("92.67");
    assertThat(personal.getReferenceScore()).isEqualByComparingTo("92.67");
    assertThat(company.getProfessionalScore()).isEqualByComparingTo("83.33");
  }

  @Test
  void zeroWeightedDimensionKeepsItsEvidenceButDoesNotContributeToPersonalScore() {
    var personalWeights = new WeightProfileVersion("personal-zero", "personal", 3, 100, 0, 0, 0, 0);
    var result = engine.evaluate(
        jd(), resume(), plan(true, true, true, true, true), personalWeights, List.of(), Set.of(), 0);
    assertThat(result.getDimensions())
        .extracting(MatchDimensionResultDTO::getConfiguredWeight)
        .containsExactly(100, 0, 0, 0, 0);
    assertThat(result.getDimensions())
        .extracting(MatchDimensionResultDTO::getScore)
        .containsExactly(new BigDecimal("100.00"), new BigDecimal("100.00"),
            new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("66.67"));
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("100.00");
  }

  @Test
  void recognizesExplicitProjectTechnologiesAsSkillEvidence() {
    JdFactSnapshotData sourceJd = jd();
    JdFactSnapshotData pythonJd = new JdFactSnapshotData(
        sourceJd.getSnapshotId(), sourceJd.getJdVersionId(), sourceJd.getSnapshotVersion(),
        new ProfessionalFacts(
            List.of(new Skill("python", "Python", true, List.of())), List.of(), null, null,
            List.of(), List.of(), List.of()),
        sourceJd.getControlled(), sourceJd.getFulfillment());
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData projectOnly = new ResumeFactSnapshotData(
        source.getSnapshotId(), source.getResumeVersionId(), source.getCandidateId(), source.getSnapshotVersion(),
        source.getCandidate(), source.getWorkExperiences(),
        List.of(new ResumeFactSnapshotData.Project(
            "p-python", "RAG 问答", "开发", null, null, "智能问答", List.of("Python", "RAG"), List.of())),
        source.getEducations(), List.of(), source.getSimpleFacts());

    var result = engine.evaluate(
        pythonJd, projectOnly, plan(true, false, false, false, false), weights(), List.of(), Set.of(), 0);

    assertThat(result.getDimensions().getFirst().getScore()).isEqualByComparingTo("100.00");
  }

  @Test
  void keepsFiveDimensionsWhenJdOnlyNamesSkills() {
    JdFactSnapshotData source = jd();
    JdFactSnapshotData skillsOnly = new JdFactSnapshotData(
        source.getSnapshotId(), source.getJdVersionId(), source.getSnapshotVersion(),
        new ProfessionalFacts(
            List.of(new Skill("java", "Java", true, List.of())), List.of(), null, null,
            List.of(), List.of(), List.of()),
        source.getControlled(), source.getFulfillment());

    var result = engine.evaluate(
        skillsOnly, resume(), plan(true, true, true, true, true), weights(), List.of(), Set.of(), 0);

    assertThat(result.getDimensions()).extracting(MatchDimensionResultDTO::isEnabled)
        .containsExactly(true, true, true, true, true);
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("40.00");
  }

  @Test
  void doesNotTreatEmbeddedAsciiTechnicalTermsAsSkillMatches() {
    JdFactSnapshotData jd = new JdFactSnapshotData(
        "jf", "jdv", 1,
        new ProfessionalFacts(
            List.of(new Skill("s1", "Java", true, List.of()), new Skill("s2", "Go", true, List.of())),
            List.of(), null, null, List.of(), List.of(), List.of()),
        new ControlledFacts(null, null, null, List.of(), List.of(), List.of()),
        new FulfillmentFacts(List.of(), null, null, null, null));
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData resume = new ResumeFactSnapshotData(
        source.getSnapshotId(), source.getResumeVersionId(), source.getCandidateId(), source.getSnapshotVersion(),
        source.getCandidate(), source.getWorkExperiences(), List.of(), source.getEducations(),
        List.of(
            new ResumeFactSnapshotData.Skill("s1", "JavaScript", null, null),
            new ResumeFactSnapshotData.Skill("s2", "MongoDB", null, null)),
        source.getSimpleFacts());

    var result = engine.evaluate(jd, resume, plan(true, false, false, false, false), weights(), List.of(), Set.of(), 0);

    assertThat(result.getDimensions().getFirst().getScore()).isEqualByComparingTo("0.00");
    assertThat(result.getDimensions().getFirst().getHighlights()).isEmpty();
  }

  @Test
  void keepsNaturalLanguageMatchesWhenTheAsciiTermIsACompleteWord() {
    JdFactSnapshotData jd = new JdFactSnapshotData(
        "jf", "jdv", 1,
        new ProfessionalFacts(List.of(), List.of(), null, null, List.of("Java"), List.of(), List.of()),
        new ControlledFacts(null, null, null, List.of(), List.of(), List.of()),
        new FulfillmentFacts(List.of(), null, null, null, null));
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData resume = new ResumeFactSnapshotData(
        source.getSnapshotId(), source.getResumeVersionId(), source.getCandidateId(), source.getSnapshotVersion(),
        source.getCandidate(),
        List.of(new ResumeFactSnapshotData.Work(
            "w1", "示例公司", "工程师", null, null, "负责 Java 开发", List.of(), List.of())),
        source.getProjectExperiences(), source.getEducations(), source.getSkills(), source.getSimpleFacts());

    var result = engine.evaluate(jd, resume, plan(false, false, false, true, false), weights(), List.of(), Set.of(), 0);

    assertThat(result.getDimensions().get(3).getScore()).isEqualByComparingTo("100.00");
    assertThat(result.getDimensions().get(3).getHighlights()).containsExactly("Java ← 负责 Java 开发");
  }

  @Test
  void scoresParsedResponsibilitiesIndependentlyWithoutSourceEvidence() {
    JdFactSnapshotData jd = new JdFactSnapshotData(
        "jf", "jdv", 1,
        new ProfessionalFacts(
            List.of(), List.of(), null, null,
            List.of("模型微调", "模型优化", "数据清洗"), List.of(), List.of()),
        new ControlledFacts(null, null, null, List.of(), List.of(), List.of()),
        new FulfillmentFacts(List.of(), null, null, null, null));
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData resume = new ResumeFactSnapshotData(
        source.getSnapshotId(), source.getResumeVersionId(), source.getCandidateId(),
        source.getSnapshotVersion(), source.getCandidate(),
        List.of(new ResumeFactSnapshotData.Work(
            "w1", "示例公司", "AI工程师", null, null, "负责模型微调", List.of(), List.of())),
        source.getProjectExperiences(), source.getEducations(), source.getSkills(),
        source.getSimpleFacts());

    var result = engine.evaluate(
        jd, resume, plan(false, false, false, true, false), weights(), List.of(), Set.of(), 0);

    assertThat(result.getDimensions().get(3).getScore()).isEqualByComparingTo("33.33");
    assertThat(result.getDimensions().get(3).getExplanation()).isEqualTo("1/3 组 JD 职责来源命中");
  }

  @Test
  void legacyDimensionSwitchesCannotRemoveDimensionsAndUnknownDoesNotRedistributeWeight() {
    var disabled =
        engine.evaluate(
            jd(),
            resume(),
            plan(false, true, false, false, false),
            weights(),
            List.of(),
            Set.of(),
            0);
    assertThat(disabled.getDimensions()).allSatisfy(dimension -> assertThat(dimension.isEnabled()).isTrue());
    assertThat(disabled.getProfessionalScore()).isEqualByComparingTo("83.33");
    ResumeFactSnapshotData noYears =
        new ResumeFactSnapshotData(
            "rf",
            "rv",
            "candidate",
            1,
            resume().getCandidate(),
            resume().getWorkExperiences().stream()
                .map(
                    v ->
                        new ResumeFactSnapshotData.Work(
                            v.itemKey(),
                            v.employer(),
                            v.role(),
                            null,
                            null,
                            v.description(),
                            v.responsibilities(),
                            v.achievements()))
                .toList(),
            resume().getProjectExperiences(),
            resume().getEducations(),
            resume().getSkills().stream()
                .map(v -> new ResumeFactSnapshotData.Skill(v.itemKey(), v.name(), v.level(), null))
                .toList(),
            resume().getSimpleFacts());
    var unknown =
        engine.evaluate(
            jd(),
            noYears,
            plan(false, false, true, false, false),
            weights(),
            List.of(),
            Set.of(),
            0);
    assertThat(unknown.getDimensions().get(2).getStatus()).isEqualTo("UNKNOWN");
    assertThat(unknown.getProfessionalScore()).isEqualByComparingTo("63.33");
  }

  @Test
  void keepsMustTierSeparateAndAwardsOnlySelectedSatisfiedPreferUpToCap() {
    List<ControlledCriterion> criteria =
        List.of(
            criterion("must-language", "LANGUAGE", "MUST", "{\"values\":[\"英语\"]}", 0, false),
            criterion("prefer-degree", "EDUCATION", "PREFER", "{\"values\":[\"本科\"]}", 20, true),
            criterion("prefer-cert", "CERTIFICATE", "PREFER", "{\"values\":[\"PMP\"]}", 10, true),
            criterion("display", "AGE", "DISPLAY_ONLY", "{\"max\":45}", 0, false));
    var result =
        engine.evaluate(
            jd(),
            resume(),
            plan(true, true, true, true, true),
            weights(),
            criteria,
            Set.of("prefer-degree"),
            30);
    assertThat(result.getMustTier()).isEqualTo("PASS");
    assertThat(result.getSelectedBonusCap()).isEqualTo(20);
    assertThat(result.getBonusScore()).isEqualTo(20);
    assertThat(result.getReferenceScore()).isEqualByComparingTo("103.33");
    assertThat(result.getCriteria())
        .filteredOn(v -> v.getCode().equals("AGE"))
        .singleElement()
        .extracting(MatchCriterionResultDTO::getStatus)
        .isEqualTo("PASS");
  }

  @Test
  void personalCertificateBonusIsEvidenceBasedAndIndependentOfProfessionalWeights() {
    ResumeFactSnapshotData certified = resumeWithCredential("PMP 项目管理专业人士认证", true);
    WeightProfileVersion skillWeighted = personalCertificateWeights(
        "certificate-skill", 96, 1, 1, 1, 1, "PREFER", 10);
    WeightProfileVersion responsibilityWeighted = personalCertificateWeights(
        "certificate-responsibility", 1, 1, 1, 96, 1, "PREFER", 10);

    var skillResult = engine.evaluate(
        jd(), certified, plan(true, true, true, true, true), skillWeighted, List.of(), Set.of(), 0);
    var responsibilityResult = engine.evaluate(
        jd(), certified, plan(true, true, true, true, true), responsibilityWeighted,
        List.of(), Set.of(), 0);
    var unsupportedResult = engine.evaluate(
        jd(), resumeWithCredential("PMP 项目管理专业人士认证", false),
        plan(true, true, true, true, true), skillWeighted, List.of(), Set.of(), 0);
    var boundaryResult = engine.evaluate(
        jd(), resumeWithCredential("XPMP2 高级证书", true),
        plan(true, true, true, true, true), skillWeighted, List.of(), Set.of(), 0);

    assertThat(skillResult.getProfessionalScore()).isEqualByComparingTo("99.17");
    assertThat(responsibilityResult.getProfessionalScore()).isEqualByComparingTo("51.67");
    assertThat(skillResult.getBonusScore()).isEqualTo(10);
    assertThat(responsibilityResult.getBonusScore()).isEqualTo(10);
    assertThat(skillResult.getReferenceScore()).isEqualByComparingTo("109.17");
    assertThat(responsibilityResult.getReferenceScore()).isEqualByComparingTo("61.67");
    assertThat(skillResult.getSelectedBonusCap()).isEqualTo(10);
    assertThat(skillResult.getCriteria())
        .singleElement()
        .satisfies(criterion -> {
          assertThat(criterion.getCode()).isEqualTo("CERTIFICATE");
          assertThat(criterion.getStatus()).isEqualTo("PASS");
          assertThat(criterion.isSelected()).isTrue();
          assertThat(criterion.getConfiguredBonus()).isEqualTo(10);
          assertThat(criterion.getAwardedBonus()).isEqualTo(10);
        });
    assertThat(unsupportedResult.getBonusScore()).isZero();
    assertThat(unsupportedResult.getCriteria().getFirst().getStatus()).isEqualTo("UNKNOWN");
    assertThat(boundaryResult.getBonusScore()).isZero();
    assertThat(boundaryResult.getCriteria().getFirst().getStatus()).isEqualTo("UNKNOWN");
  }

  @Test
  void personalMustCertificateNeverAddsPointsAndMissingFactStaysUnknown() {
    WeightProfileVersion mustCertificate = personalCertificateWeights(
        "certificate-must", 96, 1, 1, 1, 1, "MUST", 0);
    var verified = engine.evaluate(
        jd(), resumeWithCredential("PMP 项目管理专业人士认证", true),
        plan(true, true, true, true, true), mustCertificate, List.of(), Set.of(), 0);
    var unsupported = engine.evaluate(
        jd(), resumeWithCredential("PMP 项目管理专业人士认证", false),
        plan(true, true, true, true, true), mustCertificate, List.of(), Set.of(), 0);

    assertThat(verified.getMustTier()).isEqualTo("PASS");
    assertThat(verified.getBonusScore()).isZero();
    assertThat(verified.getReferenceScore()).isEqualByComparingTo(verified.getProfessionalScore());
    assertThat(verified.getCriteria().getFirst().getAwardedBonus()).isZero();
    assertThat(unsupported.getMustTier()).isEqualTo("UNKNOWN");
    assertThat(unsupported.getBonusScore()).isZero();
    assertThat(unsupported.getReferenceScore())
        .isEqualByComparingTo(unsupported.getProfessionalScore());
    assertThat(unsupported.getCriteria().getFirst().getStatus()).isEqualTo("UNKNOWN");
  }

  @Test
  void limitsHighlightsAcrossAllDimensionsToTheDocumentedMaximum() {
    var result =
        engine.evaluate(
            jd(), resume(), plan(true, true, true, true, true), weights(), List.of(), Set.of(), 0);

    List<String> highlights =
        result.getDimensions().stream().flatMap(value -> value.getHighlights().stream()).toList();

    assertThat(highlights).hasSize(MAX_MATCH_HIGHLIGHTS).doesNotHaveDuplicates();
  }

  @Test
  void limitsGeneratedHighlightTextByUnicodeCodePointLength() {
    var result =
        engine.evaluate(
            jd(),
            resumeWithLongDescription(),
            plan(false, false, false, true, false),
            weights(),
            List.of(),
            Set.of(),
            0);

    assertThat(result.getDimensions().get(3).getHighlights())
        .singleElement()
        .satisfies(
            value ->
                assertThat(value.codePointCount(0, value.length()))
                    .isEqualTo(MAX_MATCH_HIGHLIGHT_TEXT_LENGTH));
  }

  @Test
  void rejectsMalformedPersistedCriterionJsonInsteadOfScoringItAsMismatch() {
    ControlledCriterion criterion =
        criterion("must-language", "LANGUAGE", "MUST", "not-json", 0, false);

    assertInvalidPersistedConfiguration(criterion);
  }

  @Test
  void rejectsUnknownPersistedCriterionOperatorInsteadOfScoringItAsMismatch() {
    ControlledCriterion criterion =
        criterion("must-language", "LANGUAGE", "MUST", "{\"values\":[\"英语\"]}", 0, false);
    criterion.setOperator("UNSUPPORTED");

    assertInvalidPersistedConfiguration(criterion);
  }

  @ParameterizedTest
  @CsvSource(value = {"MUST,UNSUPPORTED", "PREFER,UNSUPPORTED", "DISPLAY_ONLY,UNSUPPORTED",
      "MUST,NULL", "MUST,''"}, nullValues = "NULL")
  void missingFactsCannotHideInvalidPersistedOperators(String level, String operator) {
    ControlledCriterion criterion =
        criterion("cert", "CERTIFICATE", level, "{\"value\":\"PMP\"}", 0, true);
    criterion.setOperator(operator);

    assertInvalidPersistedConfiguration(criterion);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MUST", "PREFER", "DISPLAY_ONLY"})
  void missingFactsWithValidOperatorRemainUnknown(String level) {
    var criterion = criterion("cert", "CERTIFICATE", level, "{\"value\":\"PMP\"}",
        "PREFER".equals(level) ? 10 : 0, false);
    criterion.setOperator(" in ");

    var result = engine.evaluate(jd(), resume(), plan(true, true, true, true, true),
        weights(), List.of(criterion), Set.of(), 10);

    assertThat(result.getCriteria()).singleElement().satisfies(item -> {
      assertThat(item.getStatus()).isEqualTo("UNKNOWN");
      assertThat(item.getActual()).isEmpty();
      assertThat(item.getAwardedBonus()).isZero();
    });
    assertThat(result.getMustTier()).isEqualTo("MUST".equals(level) ? "UNKNOWN" : "PASS");
    assertThat(result.getBonusScore()).isZero();
    assertThat(result.getReferenceScore()).isEqualByComparingTo(result.getProfessionalScore());
  }

  @Test
  void rejectsNonNumericPersistedRangeInsteadOfScoringItAsMismatch() {
    ControlledCriterion criterion =
        criterion("display-age", "AGE", "DISPLAY_ONLY", "{\"max\":\"forty\"}", 0, false);

    assertInvalidPersistedConfiguration(criterion);
  }

  private void assertInvalidPersistedConfiguration(ControlledCriterion criterion) {
    assertThatThrownBy(
            () ->
                engine.evaluate(
                    jd(),
                    resume(),
                    plan(true, true, true, true, true),
                    weights(),
                    List.of(criterion),
                    Set.of(),
                    0))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo("MATCHING_CONFIGURATION_INVALID");
              assertThat(exception.status().value()).isEqualTo(500);
              assertThat(exception.getCause()).isNotNull();
            });
  }

  @Test
  void choiceConditionsMatchWholeValuesAndDoNotConfuseFemaleWithMale() {
    var gender = criterion("gender", "GENDER", "MUST", "{\"values\":[\"MALE\"]}", 0, false);
    var result = engine.evaluate(
        jd(), resume(), plan(true, true, true, true, true), weights(), List.of(gender), Set.of(), 0);
    assertThat(result.getCriteria().getFirst().getStatus()).isEqualTo("FAIL");
    gender.setExpectedValueJson("{\"values\":[\"女\"]}");
    result = engine.evaluate(jd(), resume(), plan(true, true, true, true, true), weights(), List.of(gender), Set.of(), 0);
    assertThat(result.getCriteria().getFirst().getStatus()).isEqualTo("PASS");
  }

  @Test
  void recognizesPostgraduateMasterAsHigherThanBachelorRegardlessOfEducationOrder() {
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData graduate =
        new ResumeFactSnapshotData(
            source.getSnapshotId(),
            source.getResumeVersionId(),
            source.getCandidateId(),
            source.getSnapshotVersion(),
            source.getCandidate(),
            source.getWorkExperiences(),
            source.getProjectExperiences(),
            List.of(
                new ResumeFactSnapshotData.Education(
                    "bachelor", "示例本科院校", "本科", "计算机", null, null, "全日制"),
                new ResumeFactSnapshotData.Education(
                    "master", "示例研究生院", "硕士研究生", "人工智能", null, null, "全日制")),
            source.getSkills(),
            source.getSimpleFacts());
    ControlledCriterion master =
        criterion("must-master", "EDUCATION", "MUST", "{\"values\":[\"硕士\"]}", 0, false);

    var result =
        engine.evaluate(
            jd(), graduate, plan(true, true, true, true, true), weights(), List.of(master), Set.of(), 0);

    assertThat(result.getMustTier()).isEqualTo("PASS");
    assertThat(result.getCriteria().getFirst().getStatus()).isEqualTo("PASS");
  }

  @Test
  void displayOnlyRecordsKnownFactsWithoutJudgingThemAgainstATarget() {
    var gender = criterion("gender", "GENDER", "DISPLAY_ONLY", "{\"values\":[\"MALE\"]}", 0, false);

    var result = engine.evaluate(
        jd(), resume(), plan(true, true, true, true, true), weights(), List.of(gender), Set.of(), 0);

    assertThat(result.getCriteria().getFirst().getStatus()).isEqualTo("PASS");
  }

  @Test
  void reportsUnsupportedRequirementsAsUnknownWhenThereIsNoRelevantFact() {
    var doctorate = criterion(
        "doctorate", "EDUCATION", "MUST", "{\"values\":[\"博士\"]}", 0, false);
    ResumeFactSnapshotData source = resume();
    ResumeFactSnapshotData withoutEducation = new ResumeFactSnapshotData(
        source.getSnapshotId(), source.getResumeVersionId(), source.getCandidateId(), source.getSnapshotVersion(),
        source.getCandidate(), source.getWorkExperiences(), source.getProjectExperiences(), List.of(),
        source.getSkills(), source.getSimpleFacts());

    var result = engine.evaluate(
        jd(), withoutEducation, plan(true, true, true, true, true), weights(), List.of(doctorate), Set.of(), 0);

    assertThat(result.getMustTier()).isEqualTo("UNKNOWN");
    assertThat(result.getCriteria().getFirst().getStatus()).isEqualTo("UNKNOWN");
  }

  private static JdFactSnapshotData jd() {
    return new JdFactSnapshotData(
        "jf",
        "jdv",
        1,
        new ProfessionalFacts(
            List.of(
                new Skill("s1", "Java", true, List.of()),
                new Skill("s2", "Kotlin", false, List.of())),
            List.of(new Role("r1", "Java工程师", null)),
            new BigDecimal("5"),
            new BigDecimal("7"),
            List.of("平台开发", "团队协作"),
            List.of("金融"),
            List.of("支付", "清算")),
        new ControlledFacts(
            null,
            null,
            null,
            List.of("本科"),
            List.of("PMP"),
            List.of(new Language("l1", "英语", "CET-6"))),
        new FulfillmentFacts(List.of(), null, null, null, null));
  }

  private static ResumeFactSnapshotData resume() {
    return new ResumeFactSnapshotData(
        "rf",
        "rv",
        "candidate",
        1,
        new ResumeFactSnapshotData.CandidateFacts(
            "匿名", null, null, "高级Java工程师", "上海", 35, "FEMALE", "Java 后端工程师"),
        List.of(
            new ResumeFactSnapshotData.Work(
                "w1",
                "示例公司",
                "Java工程师",
                LocalDate.now().minusYears(6),
                LocalDate.now(),
                "负责平台开发",
                List.of("平台开发"),
                List.of())),
        List.of(
            new ResumeFactSnapshotData.Project(
                "p1", "支付平台", "开发", null, null, "支付项目", List.of("Java"), List.of())),
        List.of(new ResumeFactSnapshotData.Education("e1", "示例大学", "本科", "计算机", null, null, "全日制")),
        List.of(new ResumeFactSnapshotData.Skill("s1", "Java", "高级", new BigDecimal("6"))),
        List.of(
            new ResumeFactSnapshotData.SimpleFact(
                "LANGUAGE", "l1", "英语", "CET-6", null, null, null),
            new ResumeFactSnapshotData.SimpleFact(
                "INDUSTRY_TAG", "i1", "金融", null, null, null, null)));
  }

  private static ResumeFactSnapshotData resumeWithLongDescription() {
    ResumeFactSnapshotData source = resume();
    return new ResumeFactSnapshotData(
        source.getSnapshotId(),
        source.getResumeVersionId(),
        source.getCandidateId(),
        source.getSnapshotVersion(),
        source.getCandidate(),
        List.of(
            new ResumeFactSnapshotData.Work(
                "w-long",
                "示例公司",
                "工程师",
                LocalDate.now().minusYears(6),
                LocalDate.now(),
                "平台开发" + "业".repeat(1_000),
                List.of(),
                List.of())),
        source.getProjectExperiences(),
        source.getEducations(),
        source.getSkills(),
        source.getSimpleFacts());
  }

  private static ResumeFactSnapshotData resumeWithCredential(String name, boolean present) {
    ResumeFactSnapshotData source = resume();
    return new ResumeFactSnapshotData(
        source.getSnapshotId(),
        source.getResumeVersionId(),
        source.getCandidateId(),
        source.getSnapshotVersion(),
        source.getCandidate(),
        source.getWorkExperiences(),
        source.getProjectExperiences(),
        source.getEducations(),
        source.getSkills(),
        present
            ? Stream.concat(
                    Stream.of(new ResumeFactSnapshotData.SimpleFact(
                        "CREDENTIAL", "credential-1", name, null, "PMI", null, null)),
                    source.getSimpleFacts().stream())
                .toList()
            : source.getSimpleFacts());
  }

  private static WeightProfileVersion personalCertificateWeights(
      String id,
      int skills,
      int role,
      int years,
      int responsibility,
      int industryProject,
      String level,
      int bonus) {
    WeightProfileVersion result = new WeightProfileVersion(
        id, "personal-profile", 1, skills, role, years, responsibility, industryProject);
    result.setScreeningSettingsJson("""
        {"defaultSortMode":"REFERENCE_SCORE","criteria":[
          {"code":"CERTIFICATE","level":"%s","operator":"IN","expectedValueJson":"{\\"values\\":[\\"PMP\\"]}","bonusPoints":%d,"reason":"岗位证书要求","evidencePolicy":"EXPLICIT_FACT_ONLY","selectableByHr":false}
        ]}
        """.formatted(level, bonus));
    return result;
  }

  private static MatchPlanVersion plan(boolean a, boolean b, boolean c, boolean d, boolean e) {
    MatchPlanVersion plan = new MatchPlanVersion();
    plan.setId("pv");
    plan.setPlanId("p");
    plan.setJdVersionId("jdv");
    plan.setVersionNo(1);
    plan.setStatus("ACTIVE");
    plan.setControlledCriterionPlanVersionId("cp");
    plan.setSkillsEnabled(a);
    plan.setRoleEnabled(b);
    plan.setYearsEnabled(c);
    plan.setResponsibilityEnabled(d);
    plan.setIndustryProjectEnabled(e);
    plan.setVersion(1);
    plan.setCreateId("u");
    plan.setConfiguredByUserId("u");
    plan.setActivatedByUserId("u");
    plan.setActivatedAt(Instant.now());
    plan.setCreateTime(Instant.now());
    plan.setUpdateTime(Instant.now());
    return plan;
  }

  private static WeightProfileVersion weights() {
    return new WeightProfileVersion("wv", "wp", 1, 20, 20, 20, 20, 20);
  }

  private static ControlledCriterion criterion(
      String id, String code, String level, String expected, int bonus, boolean selectable) {
    ControlledCriterion criterion = new ControlledCriterion();
    criterion.setId(id);
    criterion.setPlanVersionId("cp");
    criterion.setCriterionCode(code);
    criterion.setLevel(level);
    criterion.setOperator(code.equals("AGE") ? "LTE" : "IN");
    criterion.setExpectedValueJson(expected);
    criterion.setBonusPoints(bonus);
    criterion.setReason("业务条件");
    criterion.setEvidencePolicy("EXPLICIT_FACT_ONLY");
    criterion.setSelectableByHr(selectable);
    criterion.setSequenceNo(1);
    return criterion;
  }
}

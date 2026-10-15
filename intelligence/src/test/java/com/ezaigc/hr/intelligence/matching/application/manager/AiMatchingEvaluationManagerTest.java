package com.ezaigc.hr.intelligence.matching.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort;
import com.ezaigc.hr.intelligence.matching.constant.AiScoringConstants;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiProfessionalAssessmentDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.AiProfessionalDimensionDTO;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.service.PersonalScreeningSettings;
import com.ezaigc.hr.intelligence.matching.domain.service.ScoringConfigurationValidator;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.domain.entity.AiProfessionalAssessment;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.domain.service.AiProfessionalAssessmentValidator;
import com.ezaigc.hr.intelligence.matching.domain.service.AiScoreWeightingEngine;
import com.ezaigc.hr.intelligence.matching.domain.service.ControlledCriteriaEvaluator;
import com.ezaigc.hr.intelligence.matching.service.AiProfessionalAssessmentPersistenceService;
import com.ezaigc.hr.recruiting.demand.api.JdFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class AiMatchingEvaluationManagerTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AiMatchingEvaluationManager manager = new AiMatchingEvaluationManager();
  private final AiProfessionalAssessmentPersistenceService store = mock(AiProfessionalAssessmentPersistenceService.class);
  private final ProfessionalScoringModelPort model = mock(ProfessionalScoringModelPort.class);
  private final AtomicReference<AiProfessionalAssessment> record = new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();
  private final WeightProfileVersion weights = new WeightProfileVersion("default", "profile", 1, 20, 20, 20, 20, 20);
  private JdFactSnapshotData jd;
  private ResumeFactSnapshotData resume;

  @BeforeEach
  void setup() {
    jd = new JdFactSnapshotData();
    jd.setSnapshotId("jd-frozen"); jd.setJdVersionId("jd-version");
    jd.setProfessional(new JdFactSnapshotData.ProfessionalFacts(List.of(), List.of(), null, null,
        List.of("模型微调"), List.of(), List.of()));
    resume = new ResumeFactSnapshotData();
    resume.setSnapshotId("resume-frozen"); resume.setCandidateId("candidate"); resume.setResumeVersionId("resume-version");
    resume.setCandidate(new ResumeFactSnapshotData.CandidateFacts("不要发送姓名", "private@example.invalid", "123456",
        "工程师", "北京", 42, "FEMALE", "算法工程师"));
    resume.setSkills(List.of(new ResumeFactSnapshotData.Skill("skill", "LoRA", null, null)));
    resume.setWorkExperiences(List.of()); resume.setProjectExperiences(List.of());
    resume.setEducations(List.of()); resume.setSimpleFacts(List.of());
    var validator = new AiProfessionalAssessmentValidator();
    ReflectionTestUtils.setField(validator, "json", json);
    var weighting = new AiScoreWeightingEngine();
    var controlled = new ControlledCriteriaEvaluator();
    ReflectionTestUtils.setField(controlled, "json", json);
    ReflectionTestUtils.setField(weighting, "controlledCriteria", controlled);
    var ids = mock(PlatformIdGenerator.class);
    AtomicInteger sequence = new AtomicInteger();
    when(ids.newId()).thenAnswer(call -> "id-" + sequence.incrementAndGet());
    ReflectionTestUtils.setField(manager, "assessments", store);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "validator", validator);
    ReflectionTestUtils.setField(manager, "weighting", weighting);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "json", json);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "transactionTemplate", new TransactionTemplate(new RecordingTransactions()));
    when(store.findByCacheKey(anyString())).thenAnswer(call -> record.get());
    when(store.claim(any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      record.set(call.getArgument(0)); return true;
    });
    when(store.complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), any()))
        .thenAnswer(call -> {
          assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
          record.get().setResultJson(call.getArgument(2)); record.get().setStatus("SUCCEEDED"); return true;
        });
    when(model.cacheIdentity()).thenReturn(new ProfessionalScoringModelPort.ModelIdentity("synthetic", "model-v1"));
    when(model.score(any())).thenAnswer(call -> {
      calls.incrementAndGet();
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return new ProfessionalScoringModelPort.ModelResult("synthetic", "model-v1", output(), 80, 50, 10);
    });
  }

  @Test
  void firstScoreUsesAiJudgmentsAndWeightChangesReuseThemWithoutCallingModel() {
    weights.setId(DefaultScoringConstants.WEIGHT_VERSION_ID);
    var first = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    assertThat(first.getProfessionalScore()).isEqualByComparingTo("88");
    assertThat(first.getDimensions()).extracting(item -> item.getScore().intValue()).containsExactly(91, 72, 63, 54, 81);
    assertThat(first.getAlgorithmVersion()).isEqualTo("ai-professional-v2");
    assertThat(first.getAiAssessment().getDefaultProfessionalScore()).isEqualByComparingTo("88");
    weights.setId("personal-weights");
    var personalDefaultWeights = manager.evaluate(
        jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    assertThat(personalDefaultWeights.getProfessionalScore()).isEqualByComparingTo("72.20");
    assertThat(personalDefaultWeights.getAiAssessment()).isEqualTo(first.getAiAssessment());
    weights.setSkillsWeight(40); weights.setRoleWeight(20); weights.setYearsWeight(10);
    weights.setResponsibilityWeight(10); weights.setIndustryProjectWeight(20);
    var adjusted = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    assertThat(adjusted.getProfessionalScore()).isEqualByComparingTo("78.70");
    assertThat(adjusted.getAiAssessment()).isEqualTo(first.getAiAssessment());
    assertThat(adjusted.getEvaluatedOn()).isEqualTo(first.getEvaluatedOn());
    weights.setSkillsWeight(100); weights.setRoleWeight(0); weights.setYearsWeight(0);
    weights.setResponsibilityWeight(0); weights.setIndustryProjectWeight(0);
    var skillsOnly = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    assertThat(skillsOnly.getProfessionalScore()).isEqualByComparingTo("91.00");
    assertThat(skillsOnly.getDimensions()).extracting(item -> item.getConfiguredWeight())
        .containsExactly(100, 0, 0, 0, 0);
    assertThat(skillsOnly.getAiAssessment()).isEqualTo(first.getAiAssessment());
    assertThat(record.get().getResultJson()).isEqualTo(output());
    assertThat(calls).hasValue(1);
  }

  @Test
  void previewRequiresExistingAiAssessmentWithoutCallingModelOrWriting() {
    assertThatThrownBy(() -> manager.preview(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("MATCH_AI_ASSESSMENT_REQUIRED"));
    verifyNoInteractions(model);
    verify(store, never()).claim(any(), any());
  }

  @Test
  void cachedAiScoreCanBeReweightedWhenModelIsDisabled() {
    record.set(success());
    when(model.cacheIdentity()).thenThrow(new AssertionError("cached reweight must not inspect provider"));
    assertThat(manager.preview(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0).getProfessionalScore())
        .isEqualByComparingTo("72.20");
    verifyNoInteractions(model);
  }

  @Test
  void combinedAssessmentIsPersistedWithoutCallingScoringModel() {
    var precomputed = new PrecomputedAssessment("jd-frozen", AiScoringConstants.PROMPT_VERSION,
        AiScoringConstants.SCHEMA_VERSION, LocalDate.of(2026, 9, 20), "combined-provider",
        "combined-model", output(), 120, 80, 900);

    assertThat(manager.importPrecomputed(jd, resume, precomputed)).isTrue();

    verify(model, never()).score(any());
    assertThat(record.get().getStatus()).isEqualTo("SUCCEEDED");
    assertThat(record.get().getProvider()).isEqualTo("combined-provider");
    assertThat(record.get().getResultJson()).isEqualTo(output());
  }

  @Test
  void combinedAssessmentWithTwentyHighlightsStoresOnlyFourWithoutChangingScores() {
    var precomputed = new PrecomputedAssessment("jd-frozen", AiScoringConstants.PROMPT_VERSION,
        AiScoringConstants.SCHEMA_VERSION, LocalDate.of(2026, 9, 20), "combined-provider",
        "combined-model", twentyHighlightsOutput(), 120, 80, 900);

    assertThat(manager.importPrecomputed(jd, resume, precomputed)).isTrue();

    var strictValidator = new AiProfessionalAssessmentValidator();
    ReflectionTestUtils.setField(strictValidator, "json", json);
    var stored = strictValidator.validate(record.get().getResultJson(), jd, resume);
    assertThat(stored.getProfessionalScore()).isEqualByComparingTo("88");
    assertThat(stored.getDimensions()).extracting(AiProfessionalDimensionDTO::getScore)
        .containsExactly(new BigDecimal("91"), new BigDecimal("72"), new BigDecimal("63"),
            new BigDecimal("54"), new BigDecimal("81"));
    assertThat(stored.getDimensions().getFirst().getHighlights())
        .containsExactly("亮点-0-1", "亮点-0-2", "亮点-0-3", "亮点-0-4");
    assertThat(stored.getDimensions().stream().skip(1)
        .flatMap(dimension -> dimension.getHighlights().stream())).isEmpty();
    verify(model, never()).score(any());
  }

  @Test
  void modelFailureDoesNotProduceRuleBasedFallbackOrSuccess() {
    when(model.score(any())).thenThrow(new IllegalStateException("private@example.invalid provider payload"));
    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, ex -> {
          assertThat(ex.code()).isEqualTo("MATCH_AI_SCORING_FAILED");
          assertThat(ex.getCause()).isNull();
          assertThat(ex.getMessage()).doesNotContain("private@example.invalid");
        });
    verify(store, never()).complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), any());
    verify(store).fail(anyString(), anyString(), anyString(), any());
  }

  @Test
  void malformedModelScoreIsRejectedBeforePersisting() {
    when(model.score(any())).thenReturn(new ProfessionalScoringModelPort.ModelResult("synthetic", "v1", "{\"professionalScore\":100}", 1, 1, 1));
    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0))
        .isInstanceOf(PlatformException.class);
    verify(store, never()).complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), any());
  }

  @Test
  void competingClaimDoesNotIssueSecondModelCall() {
    var inProgress = success(); inProgress.setStatus("PROCESSING"); inProgress.setClaimToken("other-owner");
    record.set(inProgress);
    doReturn(false).when(store).claim(any(), any());
    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, ex -> assertThat(ex.code()).isEqualTo("MATCH_AI_ASSESSMENT_BUSY"));
    verify(model, never()).score(any());
  }

  @Test
  void expiredLeaseCannotReturnUnpersistedAiScore() {
    doReturn(false).when(store).complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyLong(), any());
    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0))
        .isInstanceOf(PlatformException.class);
    assertThat(record.get().getStatus()).isNotEqualTo("SUCCEEDED");
    verify(store, never()).fail(anyString(), anyString(), anyString(), any());
  }

  @Test
  void inputsContainBothProfessionalFactSetsButNotIdentityOrDemographics() {
    resume.setSimpleFacts(List.of(
        new ResumeFactSnapshotData.SimpleFact("RESPONSIBILITY_TAG", "r1", "模型部署职责", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("INDUSTRY_TAG", "i1", "工业领域经验", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("CREDENTIAL", "c1", "无关资格条件", null, null, null, null)));
    manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    var request = ArgumentCaptor.forClass(ProfessionalScoringModelPort.ScoreRequest.class);
    verify(model).score(request.capture());
    assertThat(request.getValue().jdFactsJson()).contains("模型微调", "professional");
    assertThat(request.getValue().resumeFactsJson()).contains("LoRA", "skills", "模型部署职责", "工业领域经验")
        .doesNotContain("evidence")
        .doesNotContain("private@example.invalid", "不要发送姓名", "FEMALE", "123456", "\"age\"", "无关资格条件");
  }

  @Test
  void cacheKeyChangesWithEitherFactSnapshotAndNotEvaluationDate() {
    String original = AiMatchingEvaluationManager.cacheKey(jd, resume);
    resume.setSnapshotId("new-resume");
    assertThat(AiMatchingEvaluationManager.cacheKey(jd, resume)).isNotEqualTo(original);
    resume.setSnapshotId("resume-frozen"); jd.setSnapshotId("new-jd");
    assertThat(AiMatchingEvaluationManager.cacheKey(jd, resume)).isNotEqualTo(original);
  }

  @Test
  void qualificationBonusAndMustUnknownRemainSeparateFromAiProfessionalScores() {
    resume.setEducations(List.of(new ResumeFactSnapshotData.Education("edu", "合成学校", "本科", "计算机", null, null, null)));
    var education = new ControlledCriterion();
    education.setId("edu-prefer"); education.setCriterionCode("EDUCATION"); education.setLevel("PREFER");
    education.setOperator("EQ"); education.setExpectedValueJson("{\"value\":\"本科\"}"); education.setBonusPoints(10);
    var certificate = new ControlledCriterion();
    certificate.setId("cert-must"); certificate.setCriterionCode("CERTIFICATE"); certificate.setLevel("MUST");
    certificate.setOperator("EQ"); certificate.setExpectedValueJson("{\"value\":\"PMP\"}");
    var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(education, certificate), Set.of(), 30);
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
    assertThat(result.getBonusScore()).isEqualTo(10);
    assertThat(result.getReferenceScore()).isEqualByComparingTo("82.20");
    assertThat(result.getMustTier()).isEqualTo("UNKNOWN");
    assertThat(result.getCriteria()).extracting(item -> item.getStatus()).containsExactly("PASS", "UNKNOWN");
  }

  @ParameterizedTest
  @ValueSource(strings = {"in", " in ", "contains"})
  @ResourceLock(Resources.LOCALE)
  void acceptedPersonalOperatorAliasesReachTheProductionEvaluator(String operator) {
    Locale previous = Locale.getDefault();
    Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
    Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.ENGLISH);
      var draft = new ControlledCriterionDraft(ControlledCriterionCode.LANGUAGE, ControlledCriterionLevel.PREFER,
          operator, "{\"values\":[\"英语\"]}", 10, "岗位要求", "EXPLICIT_FACT_ONLY", false);
      weights.setScreeningSettingsJson(PersonalScreeningSettings.validateAndSerialize(
          List.of(draft), "BONUS_SCORE", new ScoringConfigurationValidator()));
      assertThat(weights.getScreeningSettingsJson()).contains("\"operator\":\"" + operator + "\"");
      resume.setSimpleFacts(List.of(new ResumeFactSnapshotData.SimpleFact(
          "LANGUAGE", "lang-1", "英语", "CET-6", null, null, null)));
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);

      assertThat(result.getCriteria()).singleElement().satisfies(item -> {
        assertThat(item.getStatus()).isEqualTo("PASS");
        assertThat(item.getAwardedBonus()).isEqualTo(10);
      });
      assertThat(result.getBonusScore()).isEqualTo(10);
      assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
      assertThat(calls.get()).isEqualTo(1);
    } finally {
      Locale.setDefault(previous);
      Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
      Locale.setDefault(Locale.Category.FORMAT, previousFormat);
    }
  }

  @ParameterizedTest
  @CsvSource(value = {"MUST,UNSUPPORTED", "PREFER,UNSUPPORTED", "DISPLAY_ONLY,UNSUPPORTED",
      "MUST,NULL", "MUST,''"}, nullValues = "NULL")
  void missingFactsCannotHideInvalidOperatorsFromEvaluationOrPreview(String level, String operator) {
    var criterion = new ControlledCriterion();
    criterion.setId("cert"); criterion.setCriterionCode("CERTIFICATE"); criterion.setLevel(level);
    criterion.setOperator(operator); criterion.setExpectedValueJson("{\"value\":\"PMP\"}");
    criterion.setSelectableByHr(true);
    record.set(success());

    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, exception -> {
          assertThat(exception.code()).isEqualTo("MATCHING_CONFIGURATION_INVALID");
          assertThat(exception.status().value()).isEqualTo(500);
          assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        });
    assertThatThrownBy(() -> manager.preview(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, exception -> {
          assertThat(exception.code()).isEqualTo("MATCHING_CONFIGURATION_INVALID");
          assertThat(exception.status().value()).isEqualTo(500);
          assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        });
    verifyNoInteractions(model);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MUST", "PREFER", "DISPLAY_ONLY"})
  void missingFactsWithValidOperatorRemainUnknownWithoutChangingScores(String level) {
    var criterion = new ControlledCriterion();
    criterion.setId("cert"); criterion.setCriterionCode("CERTIFICATE"); criterion.setLevel(level);
    criterion.setOperator(" in "); criterion.setExpectedValueJson("{\"value\":\"PMP\"}");
    criterion.setBonusPoints("PREFER".equals(level) ? 10 : 0);

    var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), Set.of(), 10);

    assertThat(result.getCriteria()).singleElement().satisfies(item -> {
      assertThat(item.getStatus()).isEqualTo("UNKNOWN");
      assertThat(item.getActual()).isEmpty();
      assertThat(item.getAwardedBonus()).isZero();
    });
    assertThat(result.getMustTier()).isEqualTo("MUST".equals(level) ? "UNKNOWN" : "PASS");
    assertThat(result.getBonusScore()).isZero();
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
    assertThat(result.getReferenceScore()).isEqualByComparingTo(result.getProfessionalScore());
  }

  @Test
  void invalidExpectedJsonStillPrecedesInvalidOperator() {
    var criterion = new ControlledCriterion();
    criterion.setId("cert"); criterion.setCriterionCode("CERTIFICATE"); criterion.setLevel("MUST");
    criterion.setOperator("UNSUPPORTED"); criterion.setExpectedValueJson("[]");
    record.set(success());

    assertThatThrownBy(() -> manager.evaluate(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), Set.of(), 0))
        .isInstanceOfSatisfying(PlatformException.class, exception -> {
          assertThat(exception.code()).isEqualTo("MATCHING_CONFIGURATION_INVALID");
          assertThat(exception.status().value()).isEqualTo(500);
          assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class)
              .hasMessage("expected value must be a JSON object");
        });
    verifyNoInteractions(model);
  }

  @Test
  void controlledActualValuesKeepSourceOrderDuplicatesAndOnlyNonBlankFacts() {
    resume.setEducations(List.of(
        new ResumeFactSnapshotData.Education("e1", null, " 硕士 ", null, null, null, null),
        new ResumeFactSnapshotData.Education("e2", null, null, null, null, null, null),
        new ResumeFactSnapshotData.Education("e3", null, "  ", null, null, null, null),
        new ResumeFactSnapshotData.Education("e4", null, "本科", null, null, null, null),
        new ResumeFactSnapshotData.Education("e5", null, " 硕士 ", null, null, null, null)));
    resume.setSimpleFacts(List.of(
        new ResumeFactSnapshotData.SimpleFact("CREDENTIAL", "c1", "PMP", "忽略证书level", null, null, null),
        new ResumeFactSnapshotData.SimpleFact("LANGUAGE", "l1", "英语", "CET-6", null, null, null),
        new ResumeFactSnapshotData.SimpleFact("CREDENTIAL", "c2", null, null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("LANGUAGE", "l2", "英语", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("CREDENTIAL", "c3", "  ", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("LANGUAGE", "l3", "  ", "B2", null, null, null),
        new ResumeFactSnapshotData.SimpleFact("INDUSTRY_TAG", "other", "忽略其他类型", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("CREDENTIAL", "c4", "PMP", null, null, null, null),
        new ResumeFactSnapshotData.SimpleFact("LANGUAGE", "l4", null, "  ", null, null, null)));
    List<ControlledCriterion> criteria = List.of("AGE", "GENDER", "EDUCATION", "CERTIFICATE", "LANGUAGE")
        .stream().map(code -> {
          var criterion = new ControlledCriterion();
          criterion.setId(code); criterion.setCriterionCode(code); criterion.setLevel("DISPLAY_ONLY");
          criterion.setOperator("IN"); criterion.setExpectedValueJson("{\"values\":[\"无关目标\"]}");
          return criterion;
        }).toList();
    record.set(success());

    var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, criteria, Set.of(), 0);

    assertThat(result.getCriteria()).extracting(item -> item.getCode())
        .containsExactly("AGE", "GENDER", "EDUCATION", "CERTIFICATE", "LANGUAGE");
    assertThat(result.getCriteria()).extracting(item -> item.getActual()).containsExactly(
        List.of("42"), List.of("FEMALE"), List.of(" 硕士 ", "本科", " 硕士 "),
        List.of("PMP", "PMP"), List.of("英语", "CET-6", "英语", "B2"));
    assertThat(result.getCriteria()).allSatisfy(item -> {
      assertThat(item.getStatus()).isEqualTo("PASS");
      assertThat(item.getAwardedBonus()).isZero();
      assertThatThrownBy(() -> item.getActual().add("追加")).isInstanceOf(UnsupportedOperationException.class);
    });
    assertThat(result.getBonusScore()).isZero();
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
    verifyNoInteractions(model);
  }

  @ParameterizedTest
  @CsvSource(value = {
      "AGE|LTE|42|{\"max\":42}|PASS",
      "AGE|LTE|42|{\"max\":41}|FAIL",
      "AGE|GTE|42|{\"min\":42}|PASS",
      "AGE|GTE|42|{\"min\":43}|FAIL",
      "AGE|BETWEEN|42|{\"min\":42,\"max\":43}|PASS",
      "AGE|BETWEEN|42|{\"min\":41,\"max\":42}|PASS",
      "AGE|BETWEEN|42|{\"min\":43,\"max\":44}|FAIL",
      "AGE|BETWEEN|42|{\"min\":40,\"max\":41}|FAIL",
      "AGE|BETWEEN|42|{\"max\":43,\"min\":42}|PASS",
      "AGE|BETWEEN|42|{\"max\":42,\"min\":41}|PASS",
      "AGE|BETWEEN|42|{\"max\":44,\"min\":43}|FAIL",
      "AGE|BETWEEN|42|{\"max\":41,\"min\":40}|FAIL",
      "AGE|between|42|{\"max\":43,\"min\":41}|PASS",
      "AGE| between |42|{\"max\":43,\"min\":41}|PASS",
      "AGE|BETWEEN|42|{\"values\":[41,43]}|PASS",
      "AGE|BETWEEN|42|{\"values\":[43,41]}|FAIL",
      "AGE|BETWEEN|42|{\"lower\":41,\"upper\":43}|PASS",
      "AGE|BETWEEN|42|{\"upper\":43,\"lower\":41}|FAIL",
      "AGE|BETWEEN|42|{\"min\":43,\"max\":41}|FAIL",
      "AGE|LTE|42|{}|FAIL",
      "GENDER|EQ|FEMALE|{\"values\":[\"MALE\",\"女\"]}|PASS",
      "GENDER|IN|FEMALE|{\"values\":[\"MALE\",\"male\"]}|FAIL",
      "LANGUAGE|CONTAINS|JavaScript|{\"values\":[\"Java\"]}|UNKNOWN",
      "LANGUAGE|CONTAINS|熟练 Java 与 Spring|{\"values\":[\"Java\"]}|PASS"
  }, delimiter = '|')
  void singleFactsRespectChoiceAndInclusiveRangeConditions(
      String code, String operator, String actual, String expectedJson, String status) {
    if ("LANGUAGE".equals(code)) {
      resume.setSimpleFacts(List.of(new ResumeFactSnapshotData.SimpleFact(
          "LANGUAGE", "lang", actual, null, null, null, null)));
    }
    var criterion = new ControlledCriterion();
    criterion.setId("single-fact"); criterion.setCriterionCode(code); criterion.setLevel("MUST");
    criterion.setOperator(operator); criterion.setExpectedValueJson(expectedJson);
    record.set(success());

    var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), Set.of(), 0);

    assertThat(result.getCriteria()).singleElement().satisfies(item -> {
      assertThat(item.getStatus()).isEqualTo(status);
      assertThat(item.getActual()).containsExactly(actual);
      assertThat(item.getAwardedBonus()).isZero();
    });
    assertThat(result.getMustTier()).isEqualTo(status);
    assertThat(result.getBonusScore()).isZero();
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
    assertThat(result.getReferenceScore()).isEqualByComparingTo(result.getProfessionalScore());
    verifyNoInteractions(model);
  }

  @ParameterizedTest
  @CsvSource(value = {
      "MUST,0,false,42,PASS,0",
      "PREFER,10,true,42,PASS,10",
      "PREFER,10,false,42,PASS,0",
      "DISPLAY_ONLY,0,false,42,PASS,0",
      "MUST,0,false,NULL,UNKNOWN,0"
  }, nullValues = "NULL")
  void namedRangePreservesQualificationAndBonusInEvaluationAndPreview(
      String level, int configuredBonus, boolean selected, Integer age, String status, int bonus) {
    String expectedJson = "{\"max\":43,\"min\":41}";
    var draft = new ControlledCriterionDraft(ControlledCriterionCode.AGE,
        ControlledCriterionLevel.valueOf(level), " BETWEEN ", expectedJson, configuredBonus,
        "岗位明确要求", "EXPLICIT_FACT_ONLY", "PREFER".equals(level));
    var validated = new ScoringConfigurationValidator()
        .validate(true, true, true, true, true, List.of(draft));
    assertThat(validated.getCriteria().getFirst().getExpectedValueJson()).isEqualTo(expectedJson);
    resume.setCandidate(new ResumeFactSnapshotData.CandidateFacts("合成姓名", null, null,
        "工程师", "北京", age, "FEMALE", "算法工程师"));
    var criterion = new ControlledCriterion();
    criterion.setId("named-range"); criterion.setCriterionCode("AGE"); criterion.setLevel(level);
    criterion.setOperator(draft.getOperator()); criterion.setExpectedValueJson(expectedJson);
    criterion.setBonusPoints(configuredBonus); criterion.setSelectableByHr(draft.isSelectableByHr());
    Set<String> selections = selected ? Set.of(criterion.getId()) : Set.of();
    record.set(success());

    var evaluated = manager.evaluate(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), selections, 30);
    var preview = manager.preview(jd, resume, new MatchPlanVersion(), weights,
        List.of(criterion), selections, 30);

    for (var result : List.of(evaluated, preview)) {
      assertThat(result.getCriteria()).singleElement().satisfies(item -> {
        assertThat(item.getStatus()).isEqualTo(status);
        assertThat(item.getExpected()).containsExactly("41", "43");
        assertThat(item.getAwardedBonus()).isEqualTo(bonus);
      });
      assertThat(result.getMustTier()).isEqualTo("MUST".equals(level) ? status : "PASS");
      assertThat(result.getBonusScore()).isEqualTo(bonus);
      assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
      assertThat(result.getReferenceScore()).isEqualByComparingTo(new BigDecimal("72.20").add(BigDecimal.valueOf(bonus)));
    }
    assertThat(criterion.getExpectedValueJson()).isEqualTo(expectedJson);
    verifyNoInteractions(model);
  }

  @ParameterizedTest
  @CsvSource(value = {
      "MUST|GTE|{\"min\":42.000000000000001}|42|FAIL|0",
      "MUST|LTE|{\"max\":41.999999999999999}|42|FAIL|0",
      "MUST|BETWEEN|{\"min\":42.000000000000001,\"max\":43}|42|FAIL|0",
      "MUST|BETWEEN|{\"max\":43,\"min\":42.000000000000001}|42|FAIL|0",
      "MUST|BETWEEN|{\"min\":41,\"max\":41.999999999999999}|42|FAIL|0",
      "MUST|BETWEEN|{\"max\":41.999999999999999,\"min\":41}|42|FAIL|0",
      "PREFER|GTE|{\"min\":42.000000000000001}|42|FAIL|0",
      "PREFER|LTE|{\"max\":41.999999999999999}|42|FAIL|0",
      "MUST|' gte '|{\"min\":42.000000000000001}|42|FAIL|0",
      "MUST|' lte '|{\"max\":41.999999999999999}|42|FAIL|0",
      "PREFER|GTE|{\"min\":41.999999999999999}|42|PASS|10",
      "PREFER|LTE|{\"max\":42.000000000000001}|42|PASS|10",
      "MUST|BETWEEN|{\"max\":42.0,\"min\":42.0}|42|PASS|0",
      "MUST|GTE|{\"min\":42.000000000000001}|NULL|UNKNOWN|0",
      "DISPLAY_ONLY|LTE|{\"max\":41.999999999999999}|42|PASS|0",
      "MUST|EQ|{\"value\":42.0}|42|FAIL|0",
      "MUST|IN|{\"values\":[42.0]}|42|FAIL|0",
      "MUST|EQ|{\"value\":42}|42|PASS|0",
      "MUST|IN|{\"values\":[42]}|42|PASS|0"
  }, delimiter = '|', nullValues = "NULL")
  void exactRangeThresholdsKeepQualificationBonusAndPreviewConsistent(
      String level, String operator, String expectedJson, Integer age, String status, int bonus) {
    boolean prefer = "PREFER".equals(level);
    var draft = new ControlledCriterionDraft(ControlledCriterionCode.AGE,
        ControlledCriterionLevel.valueOf(level), operator, expectedJson, prefer ? 10 : 0,
        "岗位明确要求", "EXPLICIT_FACT_ONLY", prefer);
    var validated = new ScoringConfigurationValidator()
        .validate(true, true, true, true, true, List.of(draft));
    assertThat(validated.getCriteria().getFirst().getExpectedValueJson()).isEqualTo(expectedJson);
    resume.setCandidate(new ResumeFactSnapshotData.CandidateFacts("合成姓名", null, null,
        "工程师", "北京", age, "FEMALE", "算法工程师"));
    var criterion = new ControlledCriterion();
    criterion.setId("exact-range"); criterion.setCriterionCode("AGE"); criterion.setLevel(level);
    criterion.setOperator(operator); criterion.setExpectedValueJson(expectedJson);
    criterion.setBonusPoints(prefer ? 10 : 0); criterion.setSelectableByHr(prefer);
    Set<String> selections = prefer ? Set.of(criterion.getId()) : Set.of();
    record.set(success());

    for (var result : List.of(
        manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(criterion), selections, 30),
        manager.preview(jd, resume, new MatchPlanVersion(), weights, List.of(criterion), selections, 30))) {
      assertThat(result.getCriteria()).singleElement().satisfies(item -> {
        assertThat(item.getStatus()).isEqualTo(status);
        assertThat(item.getAwardedBonus()).isEqualTo(bonus);
      });
      assertThat(result.getMustTier()).isEqualTo("MUST".equals(level) ? status : "PASS");
      assertThat(result.getBonusScore()).isEqualTo(bonus);
      assertThat(result.getProfessionalScore()).isEqualByComparingTo("72.20");
      assertThat(result.getReferenceScore())
          .isEqualByComparingTo(new BigDecimal("72.20").add(BigDecimal.valueOf(bonus)));
    }
    assertThat(criterion.getExpectedValueJson()).isEqualTo(expectedJson);
    verifyNoInteractions(model);
  }

  @Test
  void unknownDimensionKeepsItsWeightWithoutInventingAnAiScore() {
    var data = json.readTree(output()).deepCopy();
    var unknown = (ObjectNode) data.get("dimensions").get(2);
    unknown.put("status", "UNKNOWN").put("score", 0);
    ((ObjectNode) data).put("professionalScore", 59.60);
    when(model.score(any())).thenReturn(new ProfessionalScoringModelPort.ModelResult("synthetic", "v1", data.toString(), 1, 1, 1));
    var result = manager.evaluate(jd, resume, new MatchPlanVersion(), weights, List.of(), Set.of(), 0);
    assertThat(result.getProfessionalScore()).isEqualByComparingTo("59.60");
    assertThat(result.getDimensions()).hasSize(5);
    assertThat(result.getDimensions().get(2).getStatus()).isEqualTo("UNKNOWN");
    assertThat(result.getDimensions().get(2).getConfiguredWeight()).isEqualTo(20);
  }

  private AiProfessionalAssessment success() {
    var stored = new AiProfessionalAssessment(); stored.setId("saved-ai"); stored.setStatus("SUCCEEDED");
    stored.setProvider("synthetic"); stored.setModel("model-v1");
    stored.setPromptVersion(AiScoringConstants.PROMPT_VERSION); stored.setSchemaVersion(AiScoringConstants.SCHEMA_VERSION);
    stored.setEvaluatedOn(LocalDate.of(2026, 9, 19)); stored.setResultJson(output()); return stored;
  }

  private String output() {
    List<String> codes = List.of("CORE_SKILLS", "ROLE_EXPERIENCE", "RELATED_YEARS", "RESPONSIBILITY_MATCH", "INDUSTRY_AND_PROJECTS");
    int[] scores = {91, 72, 63, 54, 81};
    return json.writeValueAsString(new AiProfessionalAssessmentDTO(new BigDecimal("88"), "AI 基于岗位语境作出的合成评估",
        IntStream.range(0, 5).mapToObj(i -> new AiProfessionalDimensionDTO(codes.get(i), "KNOWN", BigDecimal.valueOf(scores[i]),
            "模型判断，非词频公式", List.of())).toList()));
  }

  private String twentyHighlightsOutput() {
    ObjectNode root = (ObjectNode) json.readTree(output());
    ArrayNode original = (ArrayNode) root.get("dimensions");
    ArrayNode reversed = root.putArray("dimensions");
    for (int dimensionIndex = 4; dimensionIndex >= 0; dimensionIndex--) {
      ObjectNode dimension = (ObjectNode) original.get(dimensionIndex);
      ArrayNode highlights = dimension.putArray("highlights");
      for (int highlightIndex = 1; highlightIndex <= 4; highlightIndex++) {
        highlights.add("亮点-" + dimensionIndex + "-" + highlightIndex);
      }
      reversed.add(dimension);
    }
    return root.toString();
  }

  private static class RecordingTransactions extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) {}
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}

package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.ScoringPreparation;
import com.ezaigc.hr.talent.application.provider.CombinedIntakeModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Skill;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.domain.dto.IntakeScoringPreparationDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ResumeIntakeParsingManagerTest {
  private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
  private final ResumeDocumentExtractionPort extraction = mock(ResumeDocumentExtractionPort.class);
  private final ResumeParseModelPort model = mock(ResumeParseModelPort.class);
  private final RawAiArchivePort archive = mock(RawAiArchivePort.class);
  private final ResumeParseContractValidator validator = mock(ResumeParseContractValidator.class);
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final ResumeIntakeScoringService scoring = mock(ResumeIntakeScoringService.class);
  private final CombinedIntakeModelPort combined = mock(CombinedIntakeModelPort.class);
  private final ObjectProvider<CombinedIntakeModelPort> combinedProvider = mock(ObjectProvider.class);
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void parsesAndArchivesBeforeAnyCandidateExistsAndUsesAttemptSpecificArchive() throws Exception {
    var row = fixture();
    when(model.parse(any())).thenReturn(new ModelResult("provider", "model", "{}", 1, 2, 3));
    when(archive.archive(anyString(), eq("{}"))).thenReturn(new ArchiveResult("archive", "hash", 2, "enc"));
    var facts = facts(null);
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10)))).thenReturn(facts);
    var result = manager(validator).parse(row);
    assertThat(result.facts()).isSameAs(facts);
    assertThat(result.pagesJson()).contains("pageNo", "合成简历文本");
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class).getAgeAsOf())
        .isEqualTo(LocalDate.of(2026, 9, 10));
    verify(model).parse(argThat(request -> "resume.pdf".equals(request.fileName())
        && request.pages().getFirst().text().equals("合成简历文本")));
    verify(archive).archive("ai/raw/resume-intake/processing/lease.json", "{}");
  }

  @Test
  void refusesIncompleteDocumentWithoutInvokingModel() throws Exception {
    var row = fixture();
    when(extraction.extract(anyString(), any())).thenReturn(
        new ExtractedDocument("test", "NATIVE", 2, 1, 0, 1, 0.5,
            List.of(new ExtractedPage(1, "NATIVE", "text", 4, 1, 1, "hash"))));
    assertThatThrownBy(() -> manager(validator).parse(row)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(model, archive);
  }

  @Test
  void keepsVisualLimitWarningAndMissingContactWhenParsingNativeText() throws Exception {
    var row = fixture();
    when(extraction.extract(anyString(), any())).thenReturn(
        new ExtractedDocument("layered-v21", "NATIVE_TEXT", 1, 1, 0, 1, 1,
            List.of(new ExtractedPage(1, "NATIVE_TEXT_VISUAL_LIMIT", "合成简历文本", 6, 1, 1, "hash"))));
    when(model.parse(any())).thenReturn(new ModelResult("provider", "model", "{}", 1, 2, 3));
    when(archive.archive(anyString(), eq("{}"))).thenReturn(new ArchiveResult("archive", "hash", 2, "enc"));
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));

    var result = manager(validator).parse(row);

    var source = json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class);
    assertThat(source.getRoute()).isEqualTo("NATIVE_TEXT");
    assertThat(source.getPages().getFirst().getRoute()).isEqualTo("NATIVE_TEXT_VISUAL_LIMIT");
    assertThat(source.getFallbackPages()).isZero();
    assertThat(result.facts().candidate().phone()).isNull();
    verify(model).parse(argThat(request -> request.pages().getFirst().text().equals("合成简历文本")));
  }

  @Test
  void invalidModelOutputIsArchivedButCannotBecomeSuccessfulFacts() throws Exception {
    var row = fixture();
    when(model.parse(any())).thenReturn(new ModelResult("provider", "model", "{}", 1, 2, 3));
    when(archive.archive(anyString(), anyString())).thenReturn(new ArchiveResult("archive", "hash", 2, "enc"));
    var realValidator = new ResumeParseContractValidator();
    ReflectionTestUtils.setField(realValidator, "objectMapper", json);
    ReflectionTestUtils.setField(realValidator, "clock", Clock.systemUTC());
    assertThatThrownBy(() -> manager(realValidator).parse(row))
        .isInstanceOf(ResumeParseContractValidator.ContractViolation.class);
    verify(archive).archive("ai/raw/resume-intake/processing/lease.json", "{}");
  }

  @Test
  void linkedJdUsesOneCombinedModelCallAndFreezesAssessmentForLaterPersistence() throws Exception {
    var row = fixture();
    var intake = new IntakeScoringPreparationDTO();
    intake.setId("intake-1");
    intake.setJdVersionId("jd-version");
    intake.setSourceRecruiterUserId("user-1");
    when(intakes.findScoringPreparationIntake("processing")).thenReturn(intake);
    when(scoring.prepare("jd-version", "user-1")).thenReturn(new ScoringPreparation(
        "jd-version", "jd-snapshot", "{\"professional\":{}}",
        "score-prompt", "score-schema", LocalDate.of(2026, 9, 10)));
    when(combinedProvider.getIfAvailable()).thenReturn(combined);
    String raw = "{\"resumeFacts\":{},\"assessment\":{\"professionalScore\":80}}";
    when(combined.evaluate(any())).thenReturn(
        new CombinedIntakeModelPort.ModelResult("provider", "model", raw, 10, 20, 30));
    when(archive.archive(anyString(), eq(raw)))
        .thenReturn(new ArchiveResult("archive", "hash", 2, "enc"));
    var facts = facts(null);
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts);

    var result = manager(validator).parse(row);

    verify(combined).evaluate(argThat(request -> request.jdFactsJson().contains("professional")
        && request.pages().getFirst().text().equals("合成简历文本")));
    verifyNoInteractions(model);
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getPrecomputedAssessment()).satisfies(assessment -> {
          assertThat(assessment.jdFactSnapshotId()).isEqualTo("jd-snapshot");
          assertThat(assessment.resultJson()).contains("professionalScore");
          assertThat(assessment.durationMillis()).isEqualTo(30);
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"100.000000000000001", "72.700000000000001", "1e-400", "72.70"})
  void combinedAssessmentFreezesExactNumbersForLaterValidation(String number) throws Exception {
    var row = fixture();
    String raw = prepareCombined().replace("\"professionalScore\":80", "\"professionalScore\":" + number);
    when(combined.evaluate(any())).thenReturn(
        new CombinedIntakeModelPort.ModelResult("provider", "model", raw, 10, 20, 30));
    when(archive.archive(anyString(), eq(raw)))
        .thenReturn(new ArchiveResult("archive", "hash", 2, "enc"));
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));

    var result = manager(validator).parse(row);

    String assessment = json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getPrecomputedAssessment().resultJson();
    Map<String, BigDecimal> scores = json.readValue(assessment, new TypeReference<Map<String, BigDecimal>>() {});
    assertThat(scores.get("professionalScore")).isEqualByComparingTo(new BigDecimal(number));
    verify(archive).archive("ai/raw/resume-intake/processing/lease.json", raw);
    verifyNoInteractions(model);
  }

  @Test
  void rechecksCombinedFactsWhenSourceHasContactAndPreservesOnlyGroundedIdentity() throws Exception {
    var row = fixture("测试人员 手机 13800000000");
    String combinedRaw = prepareCombined();
    var originalFacts = facts(null, List.of(new Skill("skill_1", "设备维护", null, null)));
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(originalFacts);
    when(model.parse(any())).thenReturn(new ModelResult("provider", "standalone", "rechecked", 3, 4, 5));
    when(validator.validateModel(eq("rechecked"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts("13800000000"));
    when(archive.archive(anyString(), eq(combinedRaw)))
        .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));
    when(archive.archive(anyString(), eq("rechecked")))
        .thenReturn(new ArchiveResult("rechecked-archive", "hash", 2, "enc"));

    var result = manager(validator).parse(row);

    assertThat(result.facts().candidate().phone()).isEqualTo("13800000000");
    assertThat(result.facts().skills()).isEqualTo(originalFacts.skills());
    assertThat(result.model().modelVersion()).isEqualTo("model");
    assertThat(result.archive().objectKey()).isEqualTo("combined-archive");
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getPrecomputedAssessment()).isNotNull();
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getContactRecheckArchiveKey()).isEqualTo("rechecked-archive");
    verify(archive).archive("ai/raw/resume-intake/processing/lease.json", combinedRaw);
    verify(archive).archive("ai/raw/resume-intake/processing/lease-contact-recheck.json", "rechecked");
  }

  @Test
  void retainsCombinedFactsWhenIndependentContactIsNotInSource() throws Exception {
    var row = fixture("测试人员 手机 13800000000");
    String combinedRaw = prepareCombined();
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));
    when(model.parse(any())).thenReturn(new ModelResult("provider", "standalone", "rechecked", 3, 4, 5));
    when(validator.validateModel(eq("rechecked"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts("13900000000"));
    when(archive.archive(anyString(), eq(combinedRaw)))
        .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));
    when(archive.archive(anyString(), eq("rechecked")))
        .thenReturn(new ArchiveResult("rechecked-archive", "hash", 2, "enc"));

    var result = manager(validator).parse(row);

    assertThat(result.facts().candidate().phone()).isNull();
    assertThat(result.archive().objectKey()).isEqualTo("combined-archive");
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getPrecomputedAssessment()).isNotNull();
    assertThat(json.readValue(result.pagesJson(), IntakeParsedSourceDTO.class)
        .getContactRecheckArchiveKey()).isNull();
  }

  @Test
  void acceptsFormattedSourcePhoneOnlyWhenUniqueAndNearCandidateName() throws Exception {
    var row = fixture("测试人员 手机 138-0000-0000");
    String combinedRaw = prepareCombined();
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));
    when(model.parse(any())).thenReturn(new ModelResult("provider", "standalone", "rechecked", 3, 4, 5));
    when(validator.validateModel(eq("rechecked"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts("13800000000"));
    when(archive.archive(anyString(), eq(combinedRaw)))
        .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));
    when(archive.archive(anyString(), eq("rechecked")))
        .thenReturn(new ArchiveResult("rechecked-archive", "hash", 2, "enc"));

    var result = manager(validator).parse(row);

    assertThat(result.facts().candidate().phone()).isEqualTo("13800000000");
  }

  @Test
  void leavesAmbiguousOrThirdPartyPhoneForIdentityReview() throws Exception {
    for (String source : List.of(
        "测试人员 手机 13800000000；推荐人电话 13900000000",
        "测试人员 招聘方 HR 手机 13800000000",
        "候选人 测试人员 推荐人 李四 " + "项目经历技能".repeat(8) + " 手机 13800000000",
        "推荐人 李四 " + "项目经历技能".repeat(8) + " 手机 13800000000 候选人 测试人员")) {
      var row = fixture(source);
      String combinedRaw = prepareCombined();
      when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
          .thenReturn(facts(null));
      when(model.parse(any()))
          .thenReturn(new ModelResult("provider", "standalone", "rechecked", 3, 4, 5));
      when(validator.validateModel(eq("rechecked"), any(), eq(LocalDate.of(2026, 9, 10))))
          .thenReturn(facts("13800000000"));
      when(archive.archive(anyString(), eq(combinedRaw)))
          .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));
      when(archive.archive(anyString(), eq("rechecked")))
          .thenReturn(new ArchiveResult("rechecked-archive", "hash", 2, "enc"));

      assertThat(manager(validator).parse(row).facts().candidate().phone()).isNull();
    }
  }

  @Test
  void archiveFailureDuringContactRecheckKeepsOriginalValidFacts() throws Exception {
    var row = fixture("测试人员 手机 13800000000");
    String combinedRaw = prepareCombined();
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));
    when(model.parse(any()))
        .thenReturn(new ModelResult("provider", "standalone", "rechecked", 3, 4, 5));
    when(archive.archive(anyString(), eq(combinedRaw)))
        .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));
    when(archive.archive(anyString(), eq("rechecked")))
        .thenThrow(new IOException("archive unavailable"));

    assertThat(manager(validator).parse(row).facts().candidate().phone()).isNull();
  }

  @Test
  void doesNotRecheckCombinedFactsWithoutSourceContact() throws Exception {
    var row = fixture();
    String combinedRaw = prepareCombined();
    when(validator.validateModel(eq("{}"), any(), eq(LocalDate.of(2026, 9, 10))))
        .thenReturn(facts(null));
    when(archive.archive(anyString(), eq(combinedRaw)))
        .thenReturn(new ArchiveResult("combined-archive", "hash", 2, "enc"));

    manager(validator).parse(row);

    verifyNoInteractions(model);
  }

  private String prepareCombined() {
    var intake = new IntakeScoringPreparationDTO();
    intake.setId("intake-1");
    intake.setJdVersionId("jd-version");
    intake.setSourceRecruiterUserId("user-1");
    when(intakes.findScoringPreparationIntake("processing")).thenReturn(intake);
    when(scoring.prepare("jd-version", "user-1")).thenReturn(new ScoringPreparation(
        "jd-version", "jd-snapshot", "{\"professional\":{}}",
        "score-prompt", "score-schema", LocalDate.of(2026, 9, 10)));
    when(combinedProvider.getIfAvailable()).thenReturn(combined);
    String raw = "{\"resumeFacts\":{},\"assessment\":{\"professionalScore\":80}}";
    when(combined.evaluate(any())).thenReturn(
        new CombinedIntakeModelPort.ModelResult("provider", "model", raw, 10, 20, 30));
    return raw;
  }

  private static ResumeFacts facts(String phone) {
    return facts(phone, List.of());
  }

  private static ResumeFacts facts(String phone, List<Skill> skills) {
    return new ResumeFacts(new CandidateIdentity("测试人员", null, phone, null, null, null,
        null, null), List.of(), List.of(), List.of(), skills, List.of(), List.of(), List.of(),
        List.of(), List.of());
  }

  private ResumeIntakeProcessing fixture() throws Exception {
    return fixture("合成简历文本");
  }

  private ResumeIntakeProcessing fixture(String text) throws Exception {
    var row = new ResumeIntakeProcessing();
    row.setId("processing"); row.setLeaseOwner("lease");
    row.setOriginalFileName("resume.pdf"); row.setObjectKey("resume/hash");
    when(storage.open("resume/hash")).thenReturn(new ByteArrayInputStream(new byte[] {1}));
    when(extraction.extract(anyString(), any())).thenReturn(new ExtractedDocument("test", "NATIVE", 1,
        1, 0, 1, 1, List.of(new ExtractedPage(1, "NATIVE", text, text.length(), 1, 1, "hash"))));
    return row;
  }

  private ResumeIntakeParsingManager manager(ResumeParseContractValidator contractValidator) {
    var manager = new ResumeIntakeParsingManager();
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "storage", storage);
    ReflectionTestUtils.setField(manager, "extraction", extraction);
    ReflectionTestUtils.setField(manager, "model", model);
    ReflectionTestUtils.setField(manager, "combinedModelProvider", combinedProvider);
    ReflectionTestUtils.setField(manager, "scoring", scoring);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "archive", archive);
    ReflectionTestUtils.setField(manager, "validator", contractValidator);
    ReflectionTestUtils.setField(manager, "json", json);
    return manager;
  }
}

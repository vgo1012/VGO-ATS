package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Credential;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Education;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Language;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ProjectExperience;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Skill;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.WorkExperience;
import com.ezaigc.hr.talent.domain.dto.CorrectionMutationDTO;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.service.CandidateBirthMetadata;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.enums.ResumeFactType;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.FactCorrectionChangePersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParseSnapshotPersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.TalentProcessEventPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ResumeFactPersistenceManagerTest {

  private static final String SNAPSHOT_ID = "S".repeat(26);
  private static final String FACT_ID = "F".repeat(26);
  private static final String CORRECTION_ID = "C".repeat(26);

  @ParameterizedTest
  @CsvSource({"30, 2025-09-11", "31, 2026-09-11"})
  void correctionPreservesBirthAndOnlyDatesAnAgeThatActuallyChanges(
      int age, LocalDate expectedAsOf) {
    Fixture fixture = fixture();
    var persistence = mock(ResumeParsingPersistenceService.class);
    var previous = new FactSnapshot();
    previous.setId(SNAPSHOT_ID);
    previous.setAge(30);
    previous.setSnapshotVersion(1);
    CandidateBirthMetadata.capture(previous, "1995-03", LocalDate.of(2025, 9, 11));
    var stored = new FactSnapshot();
    stored.setId(FACT_ID);
    stored.setSnapshotVersion(2);
    when(persistence.findCurrentFactSnapshotForUpdate("version")).thenReturn(previous);
    when(persistence.findFactSnapshot(FACT_ID)).thenReturn(stored);
    Instant now = Instant.parse("2026-09-11T00:00:00Z");
    ReflectionTestUtils.setField(fixture.manager(), "clock", Clock.fixed(now, ZoneOffset.UTC));
    ReflectionTestUtils.setField(fixture.manager(), "persistence", persistence);
    ReflectionTestUtils.setField(fixture.manager(), "searchProjection", mock(ResumeSearchProjectionRecorder.class));
    ReflectionTestUtils.setField(fixture.manager(), "talentProcessEventPersistenceService",
        mock(TalentProcessEventPersistenceService.class));
    CandidateIdentity identity = new CandidateIdentity(
        "张三", null, null, null, null, age, null, null, "1995-03");
    var corrected = new ResumeFacts(identity, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());

    fixture.manager().storeCorrection("version", SNAPSHOT_ID, "actor", "核实", "HUMAN_PROVIDED",
        List.of(), List.of(), corrected);

    verify(persistence).insertFactSnapshot(FACT_ID, "version", null, SNAPSHOT_ID, 2,
        "HUMAN_CORRECTION", "张三", null, null, null, null, age, null, null,
        CandidateBirthMetadata.write("1995-03", expectedAsOf), "actor", "核实", now);
  }

  @Test
  void newAiParseCannotReplaceCurrentHumanCorrection() {
    Fixture fixture = fixture();
    var persistence = mock(ResumeParsingPersistenceService.class);
    var parseSnapshots = mock(ResumeParseSnapshotPersistenceService.class);
    var previous = new FactSnapshot();
    previous.setId(SNAPSHOT_ID);
    previous.setSourceType("HUMAN_CORRECTION");
    when(persistence.findCurrentFactSnapshotForUpdate("version")).thenReturn(previous);
    ReflectionTestUtils.setField(fixture.manager(), "persistence", persistence);
    ReflectionTestUtils.setField(fixture.manager(), "resumeParseSnapshotPersistenceService", parseSnapshots);

    assertThatThrownBy(() -> fixture.manager().storeAiFacts(
        "version", "extraction", "model", "schema", "prompt", "new-cache", emptyFacts()))
        .isInstanceOf(PlatformException.class)
        .satisfies(error -> assertThat(((PlatformException) error).code())
            .isEqualTo("HUMAN_CORRECTION_CURRENT"));

    verify(fixture.resumeVersions()).lockForRouting("version");
    verify(persistence).findCurrentFactSnapshotForUpdate("version");
    verify(parseSnapshots, never()).findParseSnapshotByCache(anyString());
    verify(persistence, never()).clearCurrentFact(anyString(), anyString(), any());
  }

  @Test
  void cachedFactsMustBelongToTheSameVersionAndRemainCurrent() {
    Fixture fixture = fixture();
    var persistence = mock(ResumeParsingPersistenceService.class);
    var parseSnapshots = mock(ResumeParseSnapshotPersistenceService.class);
    var searchProjection = mock(ResumeSearchProjectionRecorder.class);
    var current = new FactSnapshot();
    current.setId(SNAPSHOT_ID);
    current.setResumeVersionId("version");
    current.setSourceType("AI_PARSE");
    var foreign = new FactSnapshot();
    foreign.setId(FACT_ID);
    foreign.setResumeVersionId("other-version");
    foreign.setSourceType("AI_PARSE");
    when(persistence.findCurrentFactSnapshotForUpdate("version")).thenReturn(current);
    when(parseSnapshots.findParseSnapshotByCache("cache")).thenReturn("parse");
    when(persistence.findFactSnapshotByParse("parse")).thenReturn(foreign);
    ReflectionTestUtils.setField(fixture.manager(), "persistence", persistence);
    ReflectionTestUtils.setField(fixture.manager(), "resumeParseSnapshotPersistenceService", parseSnapshots);
    ReflectionTestUtils.setField(fixture.manager(), "searchProjection", searchProjection);

    assertThatThrownBy(() -> fixture.manager().storeAiFacts(
        "version", "extraction", "model", "schema", "prompt", "cache", emptyFacts()))
        .isInstanceOf(PlatformException.class)
        .satisfies(error -> assertThat(((PlatformException) error).code())
            .isEqualTo("FACT_CACHE_LINEAGE_CONFLICT"));

    verify(searchProjection, never()).factChanged(anyString(), anyString(), any());
    verify(persistence, never()).clearCurrentFact(anyString(), anyString(), any());
  }

  @Test
  void keepsPartialDatesInStoredFactsAndLeavesCalendarColumnsUnknown() {
    var fixture = fixture();
    var work = new WorkExperience("work_1", "甲公司", "工程师", "2022-03", "PRESENT", null, List.of(), List.of());
    List<WorkFact> rows = ReflectionTestUtils.invokeMethod(fixture.manager(), "toWorkFacts", SNAPSHOT_ID, List.of(work));
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getStartDate()).isNull();
    assertThat(rows.getFirst().getEndDate()).isNull();
    assertThat(rows.getFirst().getExt()).contains("2022-03", "PRESENT");
  }

  @Test
  void writesEachChildFactTypeThroughOneBatchCall() {
    Fixture fixture = fixture();

    ReflectionTestUtils.invokeMethod(fixture.manager(), "insertChildren", SNAPSHOT_ID, facts());

    ArgumentCaptor<List<WorkFact>> workCaptor = listCaptor();
    ArgumentCaptor<List<ProjectFact>> projectCaptor = listCaptor();
    ArgumentCaptor<List<EducationFact>> educationCaptor = listCaptor();
    ArgumentCaptor<List<SkillFact>> skillCaptor = listCaptor();
    ArgumentCaptor<List<SimpleFact>> simpleCaptor = listCaptor();
    verify(fixture.workFacts()).insertWorkFacts(workCaptor.capture());
    verify(fixture.projectFacts()).insertProjectFacts(projectCaptor.capture());
    verify(fixture.educationFacts()).insertEducationFacts(educationCaptor.capture());
    verify(fixture.skillFacts()).insertSkillFacts(skillCaptor.capture());
    verify(fixture.simpleFacts()).insertSimpleFacts(simpleCaptor.capture());
    verifyNoMoreInteractions(
        fixture.workFacts(),
        fixture.projectFacts(),
        fixture.educationFacts(),
        fixture.skillFacts(),
        fixture.simpleFacts());

    assertThat(workCaptor.getValue())
        .hasSize(2)
        .allSatisfy(value -> assertThat(value.getFactSnapshotId()).isEqualTo(SNAPSHOT_ID));
    assertThat(workCaptor.getValue().getFirst().getResponsibilitiesJson())
        .isEqualTo("[\"设计\",\"交付\"]");
    assertThat(projectCaptor.getValue())
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value.getTechnologiesJson()).isEqualTo("[\"Java\",\"MySQL\"]");
              assertThat(value.getStartDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            });
    assertThat(educationCaptor.getValue())
        .singleElement()
        .extracting(EducationFact::getDegreeName)
        .isEqualTo("本科");
    assertThat(skillCaptor.getValue())
        .singleElement()
        .extracting(SkillFact::getYearsValue)
        .isEqualTo(new BigDecimal("6.5"));
    assertThat(simpleCaptor.getValue())
        .extracting(SimpleFact::getFactType)
        .containsExactly(
            ResumeFactType.CREDENTIAL.getCode(),
            ResumeFactType.LANGUAGE.getCode(),
            ResumeFactType.INDUSTRY_TAG.getCode(),
            ResumeFactType.RESPONSIBILITY_TAG.getCode(),
            ResumeFactType.UNKNOWN.getCode());
    assertThat(simpleCaptor.getValue().get(2).getItemKey()).isEqualTo("industry_tag_1");
  }

  @Test
  void stopsFollowingBatchesWhenOneBatchReportsFailure() {
    Fixture fixture = fixture();
    when(fixture.projectFacts().insertProjectFacts(anyList())).thenReturn(false);

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    fixture.manager(), "insertChildren", SNAPSHOT_ID, emptyFacts()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("project facts batch persistence failed");

    verify(fixture.workFacts()).insertWorkFacts(anyList());
    verify(fixture.projectFacts()).insertProjectFacts(anyList());
    verify(fixture.educationFacts(), never()).insertEducationFacts(anyList());
    verify(fixture.skillFacts(), never()).insertSkillFacts(anyList());
    verify(fixture.simpleFacts(), never()).insertSimpleFacts(anyList());
  }

  @Test
  void writesCorrectionChangesThroughOneBatchCall() {
    Fixture fixture = fixture();
    ObjectMapper mapper = new ObjectMapper();
    List<CorrectionMutationDTO> mutations =
        List.of(
            new CorrectionMutationDTO(
                "/candidate/city", mapper.valueToTree("上海"), mapper.valueToTree("杭州")),
            new CorrectionMutationDTO(
                "/candidate/age", mapper.valueToTree(30), mapper.valueToTree(31)));

    ReflectionTestUtils.invokeMethod(
        fixture.manager(),
        "insertCorrectionChanges",
        CORRECTION_ID,
        "HUMAN_PROVIDED",
        mutations);

    ArgumentCaptor<List<FactCorrectionChange>> captor = listCaptor();
    verify(fixture.correctionChanges()).insertCorrectionChanges(captor.capture());
    assertThat(captor.getValue())
        .hasSize(2)
        .allSatisfy(
            value -> {
              assertThat(value.getId()).isEqualTo(FACT_ID);
              assertThat(value.getCorrectionId()).isEqualTo(CORRECTION_ID);
              assertThat(value.getSourceType()).isEqualTo("HUMAN_PROVIDED");
            });
    assertThat(captor.getValue().getFirst())
        .satisfies(
            value -> {
              assertThat(value.getJsonPointer()).isEqualTo("/candidate/city");
              assertThat(value.getOldValueJson()).isEqualTo("\"上海\"");
              assertThat(value.getNewValueJson()).isEqualTo("\"杭州\"");
            });
    assertThat(captor.getValue().getLast().getNewValueJson()).isEqualTo("31");
  }

  @Test
  void rejectsCorrectionWhenBatchPersistenceReportsFailure() {
    Fixture fixture = fixture();
    when(fixture.correctionChanges().insertCorrectionChanges(anyList())).thenReturn(false);

    assertThatThrownBy(
            () ->
                ReflectionTestUtils.invokeMethod(
                    fixture.manager(),
                    "insertCorrectionChanges",
                    CORRECTION_ID,
                    "HUMAN_PROVIDED",
                    List.of(
                        new CorrectionMutationDTO(
                            "/candidate/city",
                            new ObjectMapper().valueToTree("上海"),
                            new ObjectMapper().valueToTree("杭州")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fact correction changes batch persistence failed");
  }

  private static ResumeFacts facts() {
    return new ResumeFacts(
        candidate(),
        List.of(
            new WorkExperience(
                "work_1",
                "甲公司",
                "高级工程师",
                "2020-01-01",
                "2023-12-31",
                "核心系统研发",
                List.of("设计", "交付"),
                List.of("稳定性提升")),
            new WorkExperience(
                "work_2",
                "乙公司",
                "技术负责人",
                "2024-01-01",
                null,
                "平台建设",
                List.of(),
                List.of())),
        List.of(
            new ProjectExperience(
                "project_1",
                "招聘平台",
                "负责人",
                "2024-01-01",
                null,
                "平台重构",
                List.of("Java", "MySQL"),
                List.of("按期上线"))),
        List.of(
            new Education(
                "education_1",
                "示例大学",
                "本科",
                "计算机科学",
                "2012-09-01",
                "2016-06-30",
                "FULL_TIME")),
        List.of(new Skill("skill_1", "Java", "EXPERT", 6.5)),
        List.of(
            new Credential(
                "credential_1", "软件设计师", "考试中心", "2020-11-01", null)),
        List.of(new Language("language_1", "英语", "CET6")),
        List.of("互联网"),
        List.of("架构设计"),
        List.of("待确认事项"));
  }

  private static ResumeFacts emptyFacts() {
    return new ResumeFacts(
        candidate(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static CandidateIdentity candidate() {
    return new CandidateIdentity("张三", "user@example.com", "13800138000", "工程师", "上海", 30, "M", "后端开发");
  }

  private static Fixture fixture() {
    ResumeFactPersistenceManager manager = new ResumeFactPersistenceManager();
    PlatformIdGenerator ids = mock(PlatformIdGenerator.class);
    ResumeVersionPersistenceService resumeVersions = mock(ResumeVersionPersistenceService.class);
    WorkFactPersistenceService workFacts = mock(WorkFactPersistenceService.class);
    ProjectFactPersistenceService projectFacts = mock(ProjectFactPersistenceService.class);
    EducationFactPersistenceService educationFacts = mock(EducationFactPersistenceService.class);
    SkillFactPersistenceService skillFacts = mock(SkillFactPersistenceService.class);
    SimpleFactPersistenceService simpleFacts = mock(SimpleFactPersistenceService.class);
    FactCorrectionChangePersistenceService correctionChanges =
        mock(FactCorrectionChangePersistenceService.class);
    when(ids.newId()).thenReturn(FACT_ID);
    when(resumeVersions.lockForRouting(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    when(workFacts.insertWorkFacts(anyList())).thenReturn(true);
    when(projectFacts.insertProjectFacts(anyList())).thenReturn(true);
    when(educationFacts.insertEducationFacts(anyList())).thenReturn(true);
    when(skillFacts.insertSkillFacts(anyList())).thenReturn(true);
    when(simpleFacts.insertSimpleFacts(anyList())).thenReturn(true);
    when(correctionChanges.insertCorrectionChanges(anyList())).thenReturn(true);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock",
        Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));
    ReflectionTestUtils.setField(manager, "resumeVersions", resumeVersions);
    ReflectionTestUtils.setField(manager, "json", new ObjectMapper());
    ReflectionTestUtils.setField(manager, "workFactPersistenceService", workFacts);
    ReflectionTestUtils.setField(manager, "projectFactPersistenceService", projectFacts);
    ReflectionTestUtils.setField(manager, "educationFactPersistenceService", educationFacts);
    ReflectionTestUtils.setField(manager, "skillFactPersistenceService", skillFacts);
    ReflectionTestUtils.setField(manager, "simpleFactPersistenceService", simpleFacts);
    ReflectionTestUtils.setField(
        manager, "factCorrectionChangePersistenceService", correctionChanges);
    return new Fixture(
        manager,
        workFacts,
        projectFacts,
        educationFacts,
        skillFacts,
        simpleFacts,
        correctionChanges,
        resumeVersions);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> ArgumentCaptor<List<T>> listCaptor() {
    return ArgumentCaptor.forClass((Class) List.class);
  }

  private record Fixture(
      ResumeFactPersistenceManager manager,
      WorkFactPersistenceService workFacts,
      ProjectFactPersistenceService projectFacts,
      EducationFactPersistenceService educationFacts,
      SkillFactPersistenceService skillFacts,
      SimpleFactPersistenceService simpleFacts,
      FactCorrectionChangePersistenceService correctionChanges,
      ResumeVersionPersistenceService resumeVersions) {}
}

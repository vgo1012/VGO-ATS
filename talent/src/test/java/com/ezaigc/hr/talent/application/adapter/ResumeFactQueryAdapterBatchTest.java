package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.talent.api.CandidateAccessPolicy;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.ResumeFactBatch;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.ResumeResourceEntity;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ResumeFactQueryAdapterBatchTest {

  @BeforeAll
  static void initializeTableMetadata() {
    for (Class<?> entityType :
        List.of(
            FactSnapshot.class,
            WorkFact.class,
            ProjectFact.class,
            EducationFact.class,
            SkillFact.class,
            SimpleFact.class)) {
      TableInfoHelper.initTableInfo(
          new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entityType);
    }
  }

  @Test
  void returnsPreloadedSnapshotWithOneCallPerEntityService() {
    Fixture fixture = fixture();
    FactSnapshot snapshot = snapshot();
    ResumeResourceEntity resource = resource();
    when(fixture.mapper().listByIds(List.of("snapshot-1"))).thenReturn(List.of(snapshot));
    when(fixture.mapper().findResumeResources(List.of("resume-1"))).thenReturn(List.of(resource));

    ResumeFactBatch batch = fixture.adapter().snapshotFactBatch(List.of("snapshot-1"));

    assertThat(batch.get("snapshot-1"))
        .satisfies(
            facts -> {
              assertThat(facts.getSnapshotId()).isEqualTo("snapshot-1");
              assertThat(facts.getResumeVersionId()).isEqualTo("resume-1");
              assertThat(facts.getCandidateId()).isEqualTo("candidate-1");
            });
    verify(fixture.mapper()).listByIds(List.of("snapshot-1"));
    verify(fixture.mapper()).findResumeResources(List.of("resume-1"));
    verify(fixture.workFacts()).list(anyWrapper());
    verify(fixture.projectFacts()).list(anyWrapper());
    verify(fixture.educationFacts()).list(anyWrapper());
    verify(fixture.skillFacts()).list(anyWrapper());
    verify(fixture.simpleFacts()).list(anyWrapper());
    verify(fixture.mapper(), never()).findFactSnapshot(anyString());
    verify(fixture.workFacts(), never()).findWork(anyString());
  }

  @Test
  void fallsBackToSingularLookupSoOneCorruptSnapshotKeepsItsOriginalCause() {
    Fixture fixture = fixture();
    FactSnapshot snapshot = snapshot();
    ResumeResourceEntity resource = resource();
    WorkFact corruptWork = new WorkFact();
    corruptWork.setFactSnapshotId("snapshot-1");
    corruptWork.setResponsibilitiesJson("not-json");
    corruptWork.setAchievementsJson("[]");
    when(fixture.mapper().listByIds(List.of("snapshot-1"))).thenReturn(List.of(snapshot));
    when(fixture.mapper().findResumeResources(List.of("resume-1"))).thenReturn(List.of(resource));
    when(fixture.workFacts().list(anyWrapper())).thenReturn(List.of(corruptWork));
    when(fixture.mapper().findFactSnapshot("snapshot-1")).thenReturn(snapshot);
    when(fixture.mapper().findResumeResource("resume-1")).thenReturn(resource);
    when(fixture.workFacts().findWork("snapshot-1")).thenReturn(List.of(corruptWork));

    ResumeFactBatch batch = fixture.adapter().snapshotFactBatch(List.of("snapshot-1"));

    assertThatThrownBy(() -> batch.get("snapshot-1"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> {
              assertThat(error.code()).isEqualTo("RESUME_FACT_SNAPSHOT_INVALID");
              assertThat(error.getCause()).isNotNull();
            });
    verify(fixture.mapper()).findFactSnapshot("snapshot-1");
    verify(fixture.workFacts()).findWork("snapshot-1");
  }

  @Test
  void rejectsOversizedBatchBeforeQueryingPersistence() {
    Fixture fixture = fixture();
    List<String> ids = IntStream.rangeClosed(0, 1_000).mapToObj(String::valueOf).toList();

    assertThatThrownBy(() -> fixture.adapter().snapshotFactBatch(ids))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1000");
    verifyNoInteractions(fixture.mapper());
  }

  private static Fixture fixture() {
    ResumeFactQueryAdapter adapter = new ResumeFactQueryAdapter();
    ResumeParsingPersistenceService mapper = mock(ResumeParsingPersistenceService.class);
    WorkFactPersistenceService workFacts = mock(WorkFactPersistenceService.class);
    ProjectFactPersistenceService projectFacts = mock(ProjectFactPersistenceService.class);
    EducationFactPersistenceService educationFacts = mock(EducationFactPersistenceService.class);
    SkillFactPersistenceService skillFacts = mock(SkillFactPersistenceService.class);
    SimpleFactPersistenceService simpleFacts = mock(SimpleFactPersistenceService.class);
    when(workFacts.list(anyWrapper())).thenReturn(List.of());
    when(projectFacts.list(anyWrapper())).thenReturn(List.of());
    when(educationFacts.list(anyWrapper())).thenReturn(List.of());
    when(skillFacts.list(anyWrapper())).thenReturn(List.of());
    when(simpleFacts.list(anyWrapper())).thenReturn(List.of());
    when(projectFacts.findProjects(anyString())).thenReturn(List.of());
    when(educationFacts.findEducation(anyString())).thenReturn(List.of());
    when(skillFacts.findSkills(anyString())).thenReturn(List.of());
    when(simpleFacts.findSimple(anyString())).thenReturn(List.of());
    ReflectionTestUtils.setField(adapter, "json", new ObjectMapper());
    CandidateAccessPolicy candidateAccess = mock(CandidateAccessPolicy.class);
    when(candidateAccess.readableResumeVersionIds(anyMap()))
        .thenAnswer(invocation -> invocation.<Map<String, String>>getArgument(0).keySet());
    ReflectionTestUtils.setField(adapter, "candidateAccess", candidateAccess);
    ReflectionTestUtils.setField(adapter, "mapper", mapper);
    ReflectionTestUtils.setField(adapter, "workFactPersistenceService", workFacts);
    ReflectionTestUtils.setField(adapter, "projectFactPersistenceService", projectFacts);
    ReflectionTestUtils.setField(adapter, "educationFactPersistenceService", educationFacts);
    ReflectionTestUtils.setField(adapter, "skillFactPersistenceService", skillFacts);
    ReflectionTestUtils.setField(adapter, "simpleFactPersistenceService", simpleFacts);
    return new Fixture(
        adapter,
        mapper,
        workFacts,
        projectFacts,
        educationFacts,
        skillFacts,
        simpleFacts);
  }

  private static FactSnapshot snapshot() {
    FactSnapshot snapshot = new FactSnapshot();
    snapshot.setId("snapshot-1");
    snapshot.setResumeVersionId("resume-1");
    snapshot.setSnapshotVersion(1);
    return snapshot;
  }

  private static ResumeResourceEntity resource() {
    ResumeResourceEntity resource = new ResumeResourceEntity();
    resource.setResumeVersionId("resume-1");
    resource.setCandidateId("candidate-1");
    return resource;
  }

  private static <T> Wrapper<T> anyWrapper() {
    return any();
  }

  private record Fixture(
      ResumeFactQueryAdapter adapter,
      ResumeParsingPersistenceService mapper,
      WorkFactPersistenceService workFacts,
      ProjectFactPersistenceService projectFacts,
      EducationFactPersistenceService educationFacts,
      SkillFactPersistenceService skillFacts,
      SimpleFactPersistenceService simpleFacts) {}
}

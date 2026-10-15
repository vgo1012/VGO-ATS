package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.application.assembler.ResumeParsingAssembler;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import com.ezaigc.hr.talent.service.EducationFactPersistenceService;
import com.ezaigc.hr.talent.service.ProjectFactPersistenceService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import com.ezaigc.hr.talent.service.SimpleFactPersistenceService;
import com.ezaigc.hr.talent.service.SkillFactPersistenceService;
import com.ezaigc.hr.talent.service.WorkFactPersistenceService;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeListFactApplicationManagerTest {
  private final ResumeParsingPersistenceService snapshots = mock(ResumeParsingPersistenceService.class);
  private final WorkFactPersistenceService work = mock(WorkFactPersistenceService.class);
  private final ProjectFactPersistenceService projects = mock(ProjectFactPersistenceService.class);
  private final EducationFactPersistenceService education = mock(EducationFactPersistenceService.class);
  private final SkillFactPersistenceService skills = mock(SkillFactPersistenceService.class);
  private final SimpleFactPersistenceService simple = mock(SimpleFactPersistenceService.class);
  private final ResumeListFactApplicationManager manager = new ResumeListFactApplicationManager();

  @BeforeEach
  void wireDependencies() {
    ReflectionTestUtils.setField(manager, "snapshots", snapshots);
    ReflectionTestUtils.setField(manager, "work", work);
    ReflectionTestUtils.setField(manager, "projects", projects);
    ReflectionTestUtils.setField(manager, "education", education);
    ReflectionTestUtils.setField(manager, "skills", skills);
    ReflectionTestUtils.setField(manager, "simple", simple);
    ReflectionTestUtils.setField(manager, "assembler", new ResumeParsingAssembler());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void listProjectionPreservesSourceOrderWithoutSelectingLargeText() {
    for (var type : List.of(FactSnapshot.class, WorkFact.class, ProjectFact.class, EducationFact.class,
        SkillFact.class, SimpleFact.class)) {
      TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "summary"), type);
    }
    var snapshot = new FactSnapshot(); snapshot.setId("snapshot"); snapshot.setResumeVersionId("resume");
    snapshot.setGender("MALE");
    snapshot.setAge(31);
    snapshot.setCreateTime(Instant.parse("2026-09-01T00:00:00Z"));
    when(snapshots.list(any(Wrapper.class))).thenReturn(List.of(snapshot));
    var first = new WorkFact(); first.setId("work-first"); first.setFactSnapshotId("snapshot");
    first.setExt("{\"sourceIndex\":0,\"startDateText\":\"2020\",\"endDateText\":\"PRESENT\"}");
    var second = new WorkFact(); second.setId("work-second"); second.setFactSnapshotId("snapshot");
    second.setExt("{\"sourceIndex\":1}");
    when(work.list(any(Wrapper.class))).thenReturn(List.of(second, first));

    var summaries = manager.summaries(List.of("resume", "unparsed"));
    assertThat(summaries.keySet()).containsExactly("resume");
    assertThat(summaries.get("resume").getSnapshot().getGender()).isEqualTo("MALE");
    assertThat(summaries.get("resume").getSnapshot().getAge()).isEqualTo(31);
    assertThat(summaries.get("resume").getSnapshot().getCreateTime()).isEqualTo(snapshot.getCreateTime());
    assertThat(summaries.get("resume").getWorkExperiences()).extracting(value -> value.getId())
        .containsExactly("work-first", "work-second");
    assertThat(summaries.get("resume").getWorkExperiences().getFirst().getStartDateText()).isEqualTo("2020");
    ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
    verify(work).list(query.capture());
    assertThat(query.getValue().getSqlSelect()).doesNotContain("description", "responsibilities", "achievements");
    verify(snapshots).list(query.capture());
    assertThat(query.getValue().getSqlSelect().split(",")).contains("gender", "age", "create_time");
    verify(projects).list(any(Wrapper.class)); verify(education).list(any(Wrapper.class));
    verify(skills).list(any(Wrapper.class)); verify(simple).list(any(Wrapper.class));
  }

  @Test
  void emptyOrUnparsedPageDoesNotReadChildTables() {
    assertThat(manager.summaries(List.of())).isEmpty();
    verifyNoInteractions(snapshots);
    assertThat(manager.summaries(List.of("unparsed"))).isEmpty();
    verifyNoInteractions(work, projects, education, skills, simple);
  }
}

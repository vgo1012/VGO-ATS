package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionSourceApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
  private final ResumePersistenceService resumes = mock(ResumePersistenceService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final CandidateReadAccess access = mock(CandidateReadAccess.class);
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final SourceChannelPersistenceService channels = mock(SourceChannelPersistenceService.class);
  private final ResumeVersionSourceApplicationManager manager = new ResumeVersionSourceApplicationManager();

  @BeforeEach
  void wireDependencies() {
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "versions", versions);
    var readAccess = new ResumeVersionReadAccess();
    ReflectionTestUtils.setField(readAccess, "resumes", resumes);
    ReflectionTestUtils.setField(readAccess, "candidates", candidates);
    ReflectionTestUtils.setField(readAccess, "access", access);
    ReflectionTestUtils.setField(manager, "readAccess", readAccess);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "channels", channels);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
  }

  @Test
  void loadsTwentyAuthorizedHistoricalVersionsWithFixedSourceQueryCount() {
    var ids = IntStream.range(0, 20).mapToObj(i -> id("version", i)).toList();
    var versionRows = IntStream.range(0, 20)
        .mapToObj(i -> version(ids.get(i), id("resume", i), id("intake", i), "AVAILABLE"))
        .toList();
    var resumeRows = IntStream.range(0, 20)
        .mapToObj(i -> resume(id("resume", i), id("candidate", i))).toList();
    var people = IntStream.range(0, 20)
        .mapToObj(i -> candidate(id("candidate", i), "ACTIVE")).toList();
    var intakeRows = IntStream.range(0, 20)
        .mapToObj(i -> intake(id("intake", i), id("channel", i))).toList();
    var channelRows = IntStream.range(0, 20)
        .mapToObj(i -> channel(id("channel", i), "渠道" + i, i == 0 ? "DISABLED" : "ACTIVE"))
        .toList();
    when(versions.listByIds(any())).thenReturn(versionRows);
    when(resumes.listByIds(any())).thenReturn(resumeRows);
    when(candidates.listByIds(any())).thenReturn(people);
    when(access.evaluate(people)).thenReturn(new CandidateReadDecision(
        Set.copyOf(people.stream().map(Candidate::getId).toList()),
        Set.copyOf(people.stream().map(Candidate::getId).toList()), Map.of()));
    when(intakes.listByIds(any())).thenReturn(intakeRows);
    when(channels.listByIds(any())).thenReturn(channelRows);

    var result = manager.sources(ids);

    assertThat(result).hasSize(20);
    assertThat(result.getFirst().getSource().getSourceChannelName()).isEqualTo("渠道0");
    verify(intakes, times(1)).listByIds(any());
    verify(channels, times(1)).listByIds(any());
  }

  @Test
  void preservesDeduplicatedRequestOrderAndRepresentsMissingSourceAndChannel() {
    String first = id("version", 1);
    String second = id("version", 2);
    when(versions.listByIds(any())).thenReturn(List.of(
        version(first, id("resume", 1), id("intake", 1), "AVAILABLE"),
        version(second, id("resume", 2), id("intake", 2), "AVAILABLE")));
    when(resumes.listByIds(any())).thenReturn(List.of(
        resume(id("resume", 1), id("candidate", 1)),
        resume(id("resume", 2), id("candidate", 2))));
    var people = List.of(candidate(id("candidate", 1), "ACTIVE"), candidate(id("candidate", 2), "ACTIVE"));
    when(candidates.listByIds(any())).thenReturn(people);
    when(access.evaluate(people)).thenReturn(new CandidateReadDecision(
        Set.copyOf(people.stream().map(Candidate::getId).toList()),
        Set.copyOf(people.stream().map(Candidate::getId).toList()), Map.of()));
    when(intakes.listByIds(any())).thenReturn(List.of(intake(id("intake", 2), id("channel", 2))));
    when(channels.listByIds(any())).thenReturn(List.of());

    var result = manager.sources(List.of(second, first, second));

    assertThat(result).extracting("resumeVersionId").containsExactly(second, first);
    assertThat(result.getFirst().getSource()).isNotNull();
    assertThat(result.getFirst().getSource().getSourceChannelId()).isEqualTo(id("channel", 2));
    assertThat(result.getFirst().getSource().getSourceChannelName()).isNull();
    assertThat(result.getFirst().getSource().getChannelType()).isNull();
    assertThat(result.get(1).getSource()).isNull();
  }

  @Test
  void omitsMissingUnavailableDeletedAndUnauthorizedVersionsBeforeSourceQueries() {
    String allowed = id("version", 1);
    String denied = id("version", 2);
    String deletedCandidate = id("version", 3);
    String unavailable = id("version", 4);
    when(versions.listByIds(any())).thenReturn(List.of(
        version(allowed, id("resume", 1), id("intake", 1), "AVAILABLE"),
        version(denied, id("resume", 2), id("intake", 2), "AVAILABLE"),
        version(deletedCandidate, id("resume", 3), id("intake", 3), "AVAILABLE"),
        version(unavailable, id("resume", 1), id("intake", 4), "DELETED")));
    when(resumes.listByIds(any())).thenReturn(List.of(
        resume(id("resume", 1), id("candidate", 1)),
        resume(id("resume", 2), id("candidate", 2)),
        resume(id("resume", 3), id("candidate", 3))));
    var readable = candidate(id("candidate", 1), "ACTIVE");
    var deniedPerson = candidate(id("candidate", 2), "ACTIVE");
    when(candidates.listByIds(any())).thenReturn(List.of(
        readable, deniedPerson, candidate(id("candidate", 3), "DELETED")));
    when(access.evaluate(List.of(readable, deniedPerson))).thenReturn(new CandidateReadDecision(
        Set.of(readable.getId()), Set.of(), Map.of(readable.getId(), Set.of(allowed))));
    when(intakes.listByIds(any())).thenReturn(List.of(intake(id("intake", 1), id("channel", 1))));
    when(channels.listByIds(any())).thenReturn(List.of(channel(id("channel", 1), "历史渠道", "DISABLED")));

    var result = manager.sources(List.of(denied, id("missing", 1), allowed,
        deletedCandidate, unavailable));

    assertThat(result).singleElement().satisfies(row -> {
      assertThat(row.getResumeVersionId()).isEqualTo(allowed);
      assertThat(row.getSource().getSourceChannelName()).isEqualTo("历史渠道");
    });
    verify(intakes).listByIds(List.of(id("intake", 1)));
    var ordered = inOrder(access, intakes, channels);
    ordered.verify(access).evaluate(List.of(readable, deniedPerson));
    ordered.verify(intakes).listByIds(any());
    ordered.verify(channels).listByIds(any());
  }

  @Test
  void missingSessionPreventsAllOwnershipAndSourceReads() {
    when(authorization.currentActor())
        .thenThrow(PlatformExceptions.forbidden("FORBIDDEN", "无权读取"));

    assertThatThrownBy(() -> manager.sources(List.of(id("version", 1))))
        .isInstanceOf(RuntimeException.class);

    verifyNoInteractions(versions, resumes, candidates, access, intakes, channels);
  }

  @Test
  void authorizedVersionWithoutIntakeReturnsNullSourceWithoutSourceQueries() {
    String versionId = id("version", 1);
    var person = candidate(id("candidate", 1), "ACTIVE");
    when(versions.listByIds(any())).thenReturn(List.of(
        version(versionId, id("resume", 1), null, "AVAILABLE")));
    when(resumes.listByIds(any())).thenReturn(List.of(
        resume(id("resume", 1), person.getId())));
    when(candidates.listByIds(any())).thenReturn(List.of(person));
    when(access.evaluate(List.of(person))).thenReturn(new CandidateReadDecision(
        Set.of(person.getId()), Set.of(person.getId()), Map.of()));

    assertThat(manager.sources(List.of(versionId))).singleElement()
        .satisfies(row -> assertThat(row.getSource()).isNull());

    verifyNoInteractions(intakes, channels);
  }

  @Test
  void rejectsOversizedAndMalformedInputWithoutPersistenceReads() {
    assertThatThrownBy(() -> manager.sources(Collections.nCopies(101, id("version", 1))))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> manager.sources(List.of(" "))).isInstanceOf(RuntimeException.class);
    assertThat(manager.sources(List.of())).isEmpty();
    verify(authorization, times(3)).currentActor();
    verifyNoInteractions(versions, resumes, candidates, access, intakes, channels);
  }

  private static String id(String prefix, int index) {
    return prefix + "0".repeat(23 - prefix.length()) + "%03d".formatted(index);
  }

  private static ResumeVersion version(String id, String resumeId, String intakeId, String status) {
    var row = new ResumeVersion();
    row.setId(id);
    row.setResumeId(resumeId);
    row.setIntakeId(intakeId);
    row.setStatus(status);
    return row;
  }

  private static Resume resume(String id, String candidateId) {
    var row = new Resume();
    row.setId(id);
    row.setCandidateId(candidateId);
    return row;
  }

  private static Candidate candidate(String id, String status) {
    var row = new Candidate();
    row.setId(id);
    row.setStatus(status);
    return row;
  }

  private static ResumeIntake intake(String id, String channelId) {
    var row = new ResumeIntake();
    row.setId(id);
    row.setSourceChannelId(channelId);
    row.setSourceRecruiterUserId(id("recruiter", 1));
    row.setCreateTime(Instant.EPOCH);
    return row;
  }

  private static SourceChannel channel(String id, String name, String status) {
    var row = new SourceChannel();
    row.setId(id);
    row.setName(name);
    row.setChannelType("MANUAL");
    row.setStatus(status);
    return row;
  }
}

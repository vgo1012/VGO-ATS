package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.talent.application.assembler.TalentIntakeAssembler;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.CandidateResumeRow;
import com.ezaigc.hr.talent.domain.entity.ResumeIntake;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.enums.ResumeSummaryFactsStatus;
import com.ezaigc.hr.talent.domain.response.FactBundleResponse;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateResumeSummaryApplicationManagerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final CandidatePersistenceService candidates = mock(CandidatePersistenceService.class);
  private final TalentIntakePersistenceService intakes = mock(TalentIntakePersistenceService.class);
  private final ResumeVersionPersistenceService versions = mock(ResumeVersionPersistenceService.class);
  private final SourceChannelPersistenceService channels = mock(SourceChannelPersistenceService.class);
  private final ResumeListFactApplicationManager facts = mock(ResumeListFactApplicationManager.class);
  private final CandidateReadAccess candidateReadAccess = mock(CandidateReadAccess.class);
  private final CandidateResumeSummaryApplicationManager manager = new CandidateResumeSummaryApplicationManager();

  @BeforeEach
  void wireDependencies() {
    reset(authorization, candidates, intakes, versions, channels, facts, candidateReadAccess);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidates", candidates);
    ReflectionTestUtils.setField(manager, "intakes", intakes);
    ReflectionTestUtils.setField(manager, "versions", versions);
    ReflectionTestUtils.setField(manager, "channels", channels);
    ReflectionTestUtils.setField(manager, "facts", facts);
    ReflectionTestUtils.setField(manager, "assembler", new TalentIntakeAssembler());
    ReflectionTestUtils.setField(manager, "candidateReadAccess", candidateReadAccess);
    when(candidateReadAccess.evaluate(any())).thenAnswer(invocation -> {
      Collection<Candidate> rows = invocation.getArgument(0);
      Set<String> ids = rows.stream().map(Candidate::getId).collect(Collectors.toSet());
      return new CandidateReadDecision(ids, ids, Map.of());
    });
  }

  @Test
  void currentVersionsSourcesAndAuthorizedFactsAreLoadedOnceForTheWholePage() {
    var people = IntStream.rangeClosed(1, 100).mapToObj(CandidateResumeSummaryApplicationManagerTest::candidate).toList();
    var links = people.stream().map(person -> link(person.getId())).toList();
    var currentVersions = people.stream().map(person -> version(person.getId())).toList();
    when(candidates.listByIds(any())).thenReturn(people);
    when(intakes.findCurrentCandidateResumeVersions(any())).thenReturn(links);
    when(versions.listByIds(any())).thenAnswer(invocation -> {
      Collection<String> requested = invocation.getArgument(0);
      return currentVersions.stream().filter(version -> requested.contains(version.getId())).toList();
    });
    var source = new ResumeIntake(); source.setId("intake"); source.setSourceChannelId("channel");
    var channel = new SourceChannel(); channel.setId("channel"); channel.setName("实际来源");
    when(intakes.listByIds(any())).thenReturn(List.of(source));
    when(channels.listByIds(any())).thenReturn(List.of(channel));
    doReturn(new CandidateReadDecision(
        Set.of(id(1), id(2), id(3)), Set.of(id(1), id(3)), Map.of(id(2), Set.of("version-" + id(2)))))
        .when(candidateReadAccess).evaluate(any());
    var bundle = new FactBundleResponse(null, List.of(), List.of(), List.of(), List.of(), List.of());
    when(facts.summaries(any())).thenReturn(Map.of("version-" + id(1), bundle));
    var result = manager.summaries(people.stream().map(Candidate::getId).toList());

    assertThat(result).hasSize(3);
    assertThat(result.getFirst().getFacts()).isSameAs(bundle);
    assertThat(result.getFirst().getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.READY);
    assertThat(result.getFirst().getResumeVersion().getVersionNo()).isEqualTo(5);
    assertThat(result.getFirst().getSource().getSourceChannelName()).isEqualTo("实际来源");
    assertThat(result.get(1).getFacts()).isNull();
    assertThat(result.get(1).getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.NOT_READY);
    assertThat(result.get(1).getResumeVersion()).isNotNull();
    assertThat(result.get(2).getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.NOT_READY);
    verify(facts).summaries(argThat(values -> values.size() == 3
        && values.containsAll(List.of("version-" + id(1), "version-" + id(2), "version-" + id(3)))));
    verify(candidates).listByIds(any());
    verify(intakes).findCurrentCandidateResumeVersions(any());
    verify(versions).listByIds(any());
    verify(intakes).listByIds(eq(List.of("intake")));
    verify(channels).listByIds(eq(List.of("channel")));
    verify(candidateReadAccess).evaluate(any());
  }

  @Test
  void multipleJobContextsForOneResumeStillProduceOneSummary() {
    var first = link(id(1)); first.setJdVersionId("jd-a");
    var second = link(id(1)); second.setJdVersionId("jd-b");
    when(candidates.listByIds(any())).thenReturn(List.of(candidate(1)));
    when(intakes.findCurrentCandidateResumes(any())).thenReturn(List.of(first, second));
    when(intakes.findCurrentCandidateResumeVersions(any())).thenReturn(List.of(link(id(1))));
    when(versions.listByIds(any())).thenReturn(List.of(version(id(1))));

    var result = manager.summaries(List.of(id(1)));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getResumeVersion().getId()).isEqualTo("version-" + id(1));
    verify(intakes, never()).findCurrentCandidateResumes(any());
  }

  @Test
  void currentResumeWithoutJobOrScoreRemainsVisibleWhileFactsArePending() {
    when(candidates.listByIds(any())).thenReturn(List.of(candidate(1)));
    when(intakes.findCurrentCandidateResumeVersions(any())).thenReturn(List.of(link(id(1))));
    when(versions.listByIds(any())).thenReturn(List.of(version(id(1))));
    var source = new ResumeIntake(); source.setId("intake"); source.setSourceChannelId("channel");
    when(intakes.listByIds(any())).thenReturn(List.of(source));

    var result = manager.summaries(List.of(id(1))).getFirst();

    assertThat(result.getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.NOT_READY);
    assertThat(result.getResumeVersion().getId()).isEqualTo("version-" + id(1));
    assertThat(result.getSource().getIntakeId()).isEqualTo("intake");
    assertThat(result.getFacts()).isNull();
    verify(intakes, never()).findCurrentCandidateResumes(any());
  }

  @Test
  void collaboratorCannotReadTheCurrentVersionThroughAnOlderVersionGrant() {
    when(candidates.listByIds(any())).thenReturn(List.of(candidate(1)));
    when(intakes.findCurrentCandidateResumeVersions(any())).thenReturn(List.of(link(id(1))));
    doReturn(new CandidateReadDecision(Set.of(id(1)), Set.of(), Map.of(id(1), Set.of("old-version"))))
        .when(candidateReadAccess).evaluate(any());

    var result = manager.summaries(List.of(id(1))).getFirst();

    assertThat(result.getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.FORBIDDEN);
    assertThat(result.getResumeVersion()).isNull();
    assertThat(result.getSource()).isNull();
    assertThat(result.getFacts()).isNull();
    verifyNoInteractions(versions, channels);
    verify(intakes, never()).listByIds(any());
    verify(facts).summaries(List.of());
  }

  @Test
  void missingAndDeletedCandidatesAreOmittedAndInputOrderIsStableWithoutDuplicateRows() {
    var deleted = candidate(2); deleted.setStatus("DELETED");
    when(candidates.listByIds(any())).thenReturn(List.of(candidate(1), deleted, candidate(3)));
    when(intakes.findCurrentCandidateResumeVersions(any())).thenReturn(List.of());
    when(facts.summaries(List.of())).thenReturn(Map.of());
    var rows = manager.summaries(List.of(id(3), id(2), id(1), id(3), id(4)));
    assertThat(rows).extracting(row -> row.getCandidateId()).containsExactly(id(3), id(1));
    assertThat(rows).allSatisfy(row -> {
      assertThat(row.getFactsStatus()).isEqualTo(ResumeSummaryFactsStatus.NO_RESUME);
      assertThat(row.getFacts()).isNull();
      assertThat(row.getResumeVersion()).isNull();
    });
    verifyNoInteractions(versions, channels);
  }

  @Test
  void anotherUsersPrivateCandidateIsOmittedWithoutLoadingResumeMetadata() {
    when(candidates.listByIds(any())).thenReturn(List.of(candidate(1)));
    doReturn(new CandidateReadDecision(Set.of(), Set.of(), Map.of()))
        .when(candidateReadAccess).evaluate(any());

    assertThat(manager.summaries(List.of(id(1)))).isEmpty();

    verifyNoInteractions(intakes, versions, channels, facts);
  }

  @Test
  void emptyInvalidAndUnauthorizedRequestsCannotReachPersistence() {
    assertThat(manager.summaries(List.of())).isEmpty();
    assertThatThrownBy(() -> manager.summaries(Collections.nCopies(101, id(1)))).hasMessageContaining("100");
    assertThatThrownBy(() -> manager.summaries(List.of("invalid"))).hasMessageContaining("26");
    when(authorization.currentActor()).thenThrow(PlatformExceptions.forbidden("DENIED", "无权限"));
    assertThatThrownBy(() -> manager.summaries(List.of(id(1)))).hasMessageContaining("无权限");
    verifyNoInteractions(candidates, intakes, versions, channels, facts);
  }

  private static String id(int index) { return String.format("%026d", index); }
  private static Candidate candidate(int index) {
    var candidate = new Candidate(); candidate.setId(id(index)); candidate.setStatus("ACTIVE");
    candidate.setOwnerUserId("owner"); candidate.setOwnerDepartmentId("department"); return candidate;
  }
  private static CandidateResumeRow link(String id) {
    var row = new CandidateResumeRow(); row.setCandidateId(id); row.setResumeVersionId("version-" + id); return row;
  }
  private static ResumeVersion version(String id) {
    var row = new ResumeVersion(); row.setId("version-" + id); row.setIntakeId("intake");
    row.setVersionNo(5); row.setStatus("AVAILABLE"); return row;
  }
}

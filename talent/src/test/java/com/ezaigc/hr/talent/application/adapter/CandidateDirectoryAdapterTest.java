package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.common.web.error.PlatformException;
import java.util.Collections;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import java.util.Set;
import com.ezaigc.hr.talent.application.manager.CandidateReadAccess;
import com.ezaigc.hr.talent.application.manager.CandidateReadDecision;
import java.util.Map;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateDirectoryAdapterTest {

  @Test
  void newFactLockRejectsMergedCandidateEvenWhenOrdinarySnapshotIsActive() {
    var candidates = mock(CandidatePersistenceService.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    var current = new Candidate(); current.setId("candidate"); current.setStatus("DELETED");
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(current);

    assertThatThrownBy(() -> adapter.lockActive("candidate"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_NOT_FOUND"));
    verify(candidates).findCandidateForUpdate("candidate");
    verify(candidates, never()).findCandidate(any());
  }

  @Test
  void newFactLockReturnsCurrentCandidateWithoutFollowingMergeAlias() {
    var candidates = mock(CandidatePersistenceService.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    var current = new Candidate(); current.setId("candidate"); current.setStatus("ACTIVE");
    current.setFullName("当前姓名");
    when(candidates.findCandidateForUpdate("candidate")).thenReturn(current);
    assertThat(adapter.lockActive("candidate").fullName()).isEqualTo("当前姓名");
    verify(candidates, never()).findCandidate(any());
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void authorizedInterviewNamesUseOneNarrowBatchAndOmitInactiveAndMissingCandidates() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "candidate-names"), Candidate.class);
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);
    var active = new Candidate(); active.setId("active"); active.setStatus("ACTIVE"); active.setFullName("测试姓名");
    var deleted = new Candidate(); deleted.setId("deleted"); deleted.setStatus("DELETED");
    when(candidates.list(any(Wrapper.class))).thenReturn(List.of(active, deleted));

    assertThat(adapter.namesForAuthorizedCandidates(List.of("active", "deleted", "missing")))
        .containsExactlyEntriesOf(Map.of("active", "测试姓名"));

    ArgumentCaptor<LambdaQueryWrapper<Candidate>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(candidates).list(query.capture());
    assertThat(query.getValue().getSqlSelect()).isEqualTo("id,full_name,status");
    assertThat(query.getValue().getSqlSegment()).contains("id IN", "status =");
    assertThat(query.getValue().getParamNameValuePairs().values())
        .containsExactlyInAnyOrder("active", "deleted", "missing", "ACTIVE");
    verifyNoMoreInteractions(candidates);
    verifyNoInteractions(access);
  }

  @Test
  void authorizedInterviewNamesRejectOversizedBatchesAndSkipEmptyBatches() {
    var candidates = mock(CandidatePersistenceService.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    assertThat(adapter.namesForAuthorizedCandidates(List.of())).isEmpty();
    assertThatThrownBy(() -> adapter.namesForAuthorizedCandidates(Collections.nCopies(201, "candidate")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> adapter.namesForAuthorizedCandidates(null)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(candidates);
  }

  @Test
  void factReadabilityUsesTheSameCandidateTargetAsFactDetails() {
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);
    var person = new Candidate(); person.setId("person"); person.setStatus("ACTIVE");
    person.setOwnerUserId("owner"); person.setOwnerDepartmentId("department");
    when(candidates.listByIds(List.of("person"))).thenReturn(List.of(person));
    when(access.evaluate(List.of(person)))
        .thenReturn(new CandidateReadDecision(Set.of("person"), Set.of("person"), Map.of()));
    assertThat(adapter.factReadableCandidateIds(List.of("person"))).containsExactly("person");
    verify(access).evaluate(List.of(person));
    when(access.evaluate(List.of(person)))
        .thenReturn(new CandidateReadDecision(Set.of(), Set.of(), Map.of()));
    assertThat(adapter.factReadableCandidateIds(List.of("person"))).isEmpty();
  }

  @Test
  void ownershipUsesCurrentActiveCandidateRecord() {
    var candidates = mock(CandidatePersistenceService.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    var mine = new Candidate(); mine.setId("mine"); mine.setOwnerUserId("owner"); mine.setStatus("ACTIVE");
    var transferred = new Candidate(); transferred.setId("transferred"); transferred.setOwnerUserId("other"); transferred.setStatus("ACTIVE");
    var deleted = new Candidate(); deleted.setId("deleted"); deleted.setOwnerUserId("owner"); deleted.setStatus("DELETED");
    var ids = List.of("mine", "transferred", "deleted");
    when(candidates.listByIds(ids)).thenReturn(List.of(mine, transferred, deleted));

    assertThat(adapter.ownedCandidateIds(ids, "owner")).containsExactly("mine");
    assertThat(adapter.ownedCandidateIds(List.of(), "owner")).isEmpty();
  }

  @Test
  void batchKeywordMatchesCurrentNameEmailAndPhoneWithoutNullErrors() {
    var candidates = mock(CandidatePersistenceService.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    var person = new Candidate(); person.setId("person"); person.setStatus("ACTIVE"); person.setFullName("张测试");
    person.setPrimaryEmail("CURRENT@example.test"); person.setPrimaryPhone("13800000000");
    var empty = new Candidate(); empty.setId("empty"); empty.setStatus("ACTIVE");
    var access = mock(CandidateReadAccess.class);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);
    when(access.evaluate(List.of(person, empty))).thenReturn(new CandidateReadDecision(Set.of("person", "empty"), Set.of(), Map.of()));
    var ids = List.of("person", "empty");
    when(candidates.listByIds(ids)).thenReturn(List.of(person, empty));
    for (var query : List.of("张测", "current@", "138000")) {
      assertThat(adapter.matchingCandidateIds(ids, query)).containsExactly("person");
    }
    assertThat(adapter.matchingCandidateIds(ids, "missing")).isEmpty();
    when(access.evaluate(List.of(person, empty))).thenReturn(new CandidateReadDecision(Set.of(), Set.of(), Map.of()));
    assertThat(adapter.matchingCandidateIds(ids, "current@")).isEmpty();
  }

  @Test
  void readablePolicyUsesStrictCandidateObjectBoundary() {
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var candidate = new Candidate();
    candidate.setId("C".repeat(26));
    candidate.setStatus("ACTIVE");
    when(candidates.findCandidate(candidate.getId())).thenReturn(candidate);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);

    adapter.requireReadable(candidate.getId());

    verify(access).requireCandidate(candidate);
  }

  @Test
  void resumeReadablePolicyUsesTheExactCandidateResumeBoundary() {
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var candidate = new Candidate();
    candidate.setId("C".repeat(26));
    candidate.setStatus("ACTIVE");
    var resumeVersionId = "V".repeat(26);
    when(candidates.findCandidate(candidate.getId())).thenReturn(candidate);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);

    adapter.requireResumeReadable(candidate.getId(), resumeVersionId);

    verify(access).requireResume(candidate, resumeVersionId);
  }

  @Test
  void batchResumePolicyLoadsAndEvaluatesOneHundredCandidatesOnce() {
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);
    var versions = new LinkedHashMap<String, String>();
    var people = IntStream.range(0, 100).mapToObj(index -> {
      String candidateId = "candidate-" + index;
      versions.put("version-" + index, candidateId);
      var candidate = new Candidate();
      candidate.setId(candidateId);
      candidate.setStatus("ACTIVE");
      return candidate;
    }).toList();
    var candidateIds = new LinkedHashSet<>(people.stream().map(Candidate::getId).toList());
    when(candidates.listByIds(candidateIds))
        .thenReturn(people);
    when(access.evaluate(people)).thenReturn(new CandidateReadDecision(
        people.stream().map(Candidate::getId).collect(Collectors.toSet()),
        Set.of("candidate-0"),
        Map.of("candidate-1", Set.of("version-1"))));

    assertThat(adapter.readableResumeVersionIds(versions))
        .containsExactlyInAnyOrder("version-0", "version-1");
    verify(candidates).listByIds(candidateIds);
    verify(access).evaluate(people);
  }

  @Test
  void readablePolicyHidesMissingOrInactiveCandidateBeforeAuthorizationLookup() {
    var candidates = mock(CandidatePersistenceService.class);
    var access = mock(CandidateReadAccess.class);
    var candidate = new Candidate();
    candidate.setId("C".repeat(26));
    candidate.setStatus("DELETED");
    when(candidates.findCandidate(candidate.getId())).thenReturn(candidate);
    var adapter = new CandidateDirectoryAdapter();
    ReflectionTestUtils.setField(adapter, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(adapter, "candidateReadAccess", access);

    assertThatThrownBy(() -> adapter.requireReadable(candidate.getId()))
        .isInstanceOfSatisfying(
            PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("CANDIDATE_NOT_FOUND"));
  }
}

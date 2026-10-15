package com.ezaigc.hr.recruiting.recruitment.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationCandidateFilterDTO;
import com.ezaigc.hr.recruiting.recruitment.application.assembler.ApplicationAssembler;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.domain.request.ApplicationCandidateFilterRequest;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationCollaboratorPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.talent.api.CandidateDirectoryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationCandidateFilterTest {
  @Test
  void identityOnlyMatchesDoNotRequireOrReadFactsButAdditionalConditionsDo() {
    var filter = new ApplicationCandidateFilter();
    var facts = mock(ResumeFactQueryService.class);
    var candidates = mock(CandidateDirectoryService.class);
    ReflectionTestUtils.setField(filter, "facts", facts);
    ReflectionTestUtils.setField(filter, "candidates", candidates);
    when(candidates.matchingCandidateIds(List.of("person"), "current@example.test"))
        .thenReturn(Set.of("person"));
    when(candidates.factReadableCandidateIds(List.of("person"))).thenReturn(Set.of());
    var applications = List.of(application("identity", "unreadable"));
    var request = new ApplicationCandidateFilterRequest();
    request.setQ("current@example.test");
    assertThat(filter.matchingIds(applications, new ApplicationAssembler().toDTO(request)))
        .containsExactly("identity");
    verifyNoInteractions(facts);
    request.setConditions(List.of("高压电工证"));
    assertThat(filter.matchingIds(applications, new ApplicationAssembler().toDTO(request))).isEmpty();
    verifyNoInteractions(facts);
  }

  @Test
  void matchesBothCertificatesInTheApplicationVersionAndRejectsUnreadableFacts() {
    var filter = new ApplicationCandidateFilter();
    var facts = mock(ResumeFactQueryService.class);
    var candidates = mock(CandidateDirectoryService.class);
    ReflectionTestUtils.setField(filter, "facts", facts);
    ReflectionTestUtils.setField(filter, "candidates", candidates);
    ReflectionTestUtils.setField(filter, "clock", Clock.systemUTC());
    var first = application("single", "old");
    var second = application("both", "new");
    var bundle = new ResumeFactSnapshotData("snapshot", "old", "person", 1,
        new ResumeFactSnapshotData.CandidateFacts("姓名", null, null, null, null, null, null, null),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var high = new ResumeFactSnapshotData.SimpleFact("CERTIFICATE", "high", "高压电工证", null, null, null, null);
    var low = new ResumeFactSnapshotData.SimpleFact("CERTIFICATE", "low", "低压电工证", null, null, null, null);
    when(candidates.factReadableCandidateIds(List.of("person"))).thenReturn(Set.of("person"));
    when(facts.currentFactBatch(List.of("old", "new"))).thenReturn(id -> {
      bundle.setSimpleFacts(id.equals("new") ? List.of(high, low) : List.of(high));
      return bundle;
    });
    var request = new ApplicationCandidateFilterRequest();
    request.setQ("高压电工证");
    request.setConditions(List.of("低压电工证"));
    var criteria = new ApplicationAssembler().toDTO(request);
    assertThat(filter.matchingIds(List.of(first, second), criteria)).containsExactly("both");
    when(candidates.factReadableCandidateIds(List.of("person"))).thenReturn(Set.of());
    clearInvocations(facts);
    assertThat(filter.matchingIds(List.of(first, second), criteria)).isEmpty();
    verifyNoInteractions(facts);
  }

  @Test
  void filtersFrozenVersionsWithAliasesAndUnknownsWithoutCollapsingApplications() {
    var filter = new ApplicationCandidateFilter();
    var facts = mock(ResumeFactQueryService.class);
    var candidates = mock(CandidateDirectoryService.class);
    ReflectionTestUtils.setField(filter, "facts", facts);
    ReflectionTestUtils.setField(filter, "candidates", candidates);
    ReflectionTestUtils.setField(filter, "clock", Clock.systemUTC());
    var one = application("one", "frozen");
    var two = application("two", "frozen");
    var missing = application("missing", "unparsed");
    var bundle = new ResumeFactSnapshotData("snapshot", "frozen", "person", 1,
        new ResumeFactSnapshotData.CandidateFacts("旧姓名", null, null, null, null, 28, "FEMALE", null),
        List.of(new ResumeFactSnapshotData.Work("w", "企业", "开发", LocalDate.of(2020, 1, 1),
            LocalDate.of(2021, 12, 31), null, List.of(), List.of())), List.of(),
        List.of(new ResumeFactSnapshotData.Education("e", "学校", "Bachelor", null, null, null, null)),
        List.of(), List.of());
    when(candidates.factReadableCandidateIds(List.of("person"))).thenReturn(Set.of("person"));
    when(candidates.matchingCandidateIds(List.of("person"), "current@example.test")).thenReturn(Set.of("person"));
    when(facts.currentFactBatch(List.of("frozen", "unparsed"))).thenReturn(id -> {
      if (id.equals("unparsed")) throw new PlatformException(HttpStatus.CONFLICT, "RESUME_FACTS_REQUIRED", "无事实", true);
      return bundle;
    });
    var criteria = new ApplicationCandidateFilterDTO("current@example.test", List.of("本科"),
        new BigDecimal("1"), new BigDecimal("3"), 25, 29, "FEMALE");
    assertThat(filter.matchingIds(List.of(one, two, missing), criteria)).containsExactlyInAnyOrder("one", "two");
    verify(facts).currentFactBatch(List.of("frozen", "unparsed"));
    bundle.setCandidate(new ResumeFactSnapshotData.CandidateFacts("姓名", null, null, null, null, null, null, null));
    assertThat(filter.matchingIds(List.of(one, two, missing), criteria)).isEmpty();
    bundle.setCandidate(new ResumeFactSnapshotData.CandidateFacts("姓名", null, null, null, null, 28, "FEMALE", null));
    var upperBound = new ApplicationCandidateFilterDTO(null, List.of(), null, new BigDecimal("1.99"), null, null, null);
    when(facts.currentFactBatch(List.of("frozen"))).thenReturn(id -> bundle);
    assertThat(filter.matchingIds(List.of(one), upperBound)).isEmpty();
    when(candidates.factReadableCandidateIds(List.of("person"))).thenReturn(Set.of());
    clearInvocations(facts);
    assertThat(filter.matchingIds(List.of(one, two, missing), criteria)).isEmpty();
    verifyNoInteractions(facts);
  }

  @Test
  void onlyFiltersAuthorizedRowsBeforePaginationAndBindsConditionsToCursor() {
    var manager = new RecruitmentApplicationManager();
    var persistence = mock(ApplicationPersistenceService.class);
    var authorization = mock(AuthorizationService.class);
    var actor = mock(CurrentActorSnapshot.class);
    when(actor.getUserId()).thenReturn("owner");
    when(actor.getRoles()).thenReturn(Set.of());
    when(authorization.currentActor()).thenReturn(actor);
    var filter = mock(ApplicationCandidateFilter.class);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "candidateLibrary", mock(CandidateLibraryQueryService.class));
    ReflectionTestUtils.setField(manager, "applicationCandidateFilter", filter);
    ReflectionTestUtils.setField(manager, "applicationCollaboratorPersistenceService", mock(ApplicationCollaboratorPersistenceService.class));
    ReflectionTestUtils.setField(manager, "cursorCodec", new SignedCursorCodec("onboarded-filter-signing-key-32-bytes"));
    var hidden = application("hidden", "private");
    var nonmatching = application("no", "frozen");
    var one = application("one", "frozen");
    var two = application("two", "frozen");
    var firstBatch = IntStream.range(0, 200).mapToObj(index -> application("scan-" + index, "frozen")).toList();
    when(persistence.findApplications("jd", null, "owner", "ONBOARDED", null, "ALL", null, null, null, null, null, 200))
        .thenReturn(firstBatch);
    when(persistence.findApplications("jd", null, "owner", "ONBOARDED", null, "ALL", null, null, null, firstBatch.getLast().getUpdateTime(), "scan-199", 200))
        .thenReturn(List.of(hidden, nonmatching, one, two));
    var visible = firstBatch.stream().map(Application::getId).collect(Collectors.toSet());
    visible.addAll(Set.of("no", "one", "two"));
    when(authorization.accessibleObjectIds(eq(actor), eq("recruitment.manage"), anyList())).thenReturn(visible);
    var criteria = new ApplicationCandidateFilterDTO("current", List.of(), null, null, null, null, null);
    when(filter.matchingIds(List.of(nonmatching, one, two), criteria)).thenReturn(Set.of("one", "two"));
    var page = manager.list("jd", null, "ONBOARDED", null, null, null, 1, null, null, "ALL", criteria);
    assertThat(page.getItems()).containsExactly(one);
    assertThat(page.isHasMore()).isTrue();
    verify(filter).matchingIds(firstBatch, criteria);
    verify(filter).matchingIds(List.of(nonmatching, one, two), criteria);
    var changed = new ApplicationCandidateFilterDTO("changed", List.of(), null, null, null, null, null);
    assertThatThrownBy(() -> manager.list("jd", null, "ONBOARDED", null, null, page.getNextCursor(), 1, null, null, "ALL", changed))
        .isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.list("jd", null, "IN_PROCESS", null, null, null, 1, null, null, "ALL", criteria))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void normalizesEquivalentConditionsBeforeFingerprinting() {
    var request = new ApplicationCandidateFilterRequest();
    request.setQ(" NAME "); request.setGender("female"); request.setDegrees(List.of("本科", "硕士", "本科"));
    request.setMinWorkYears(new BigDecimal("1.00"));
    var assembler = new ApplicationAssembler();
    var first = assembler.toDTO(request);
    request.setQ("name"); request.setGender("FEMALE"); request.setDegrees(List.of("硕士", "本科"));
    request.setMinWorkYears(BigDecimal.ONE);
    assertThat(assembler.toDTO(request)).isEqualTo(first);
  }

  @Test
  void rejectsInvalidFilterRangesAndValues() {
    try (var factory = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory()) {
      var request = new ApplicationCandidateFilterRequest();
      request.setMinAge(30); request.setMaxAge(25);
      request.setMinWorkYears(BigDecimal.TEN); request.setMaxWorkYears(BigDecimal.ONE);
      request.setGender("INFERRED"); request.setQ("q".repeat(501));
      request.setDegrees(List.of(" "));
      assertThat(factory.getValidator().validate(request)).hasSize(5);
    }
  }

  private static Application application(String id, String resume) {
    var app = new Application(); app.setId(id); app.setCandidateId("person"); app.setResumeVersionId(resume);
    app.setUpdateTime(Instant.parse("2026-09-10T00:00:00Z"));
    return app;
  }
}

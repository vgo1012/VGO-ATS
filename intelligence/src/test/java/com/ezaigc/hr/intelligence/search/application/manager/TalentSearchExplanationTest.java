package com.ezaigc.hr.intelligence.search.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.domain.response.ConditionMatchResponse;
import com.ezaigc.hr.intelligence.search.domain.response.TalentSearchResultResponse;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.CurrentFactContact;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TalentSearchExplanationTest {
  private final ResumeFactQueryService facts = mock(ResumeFactQueryService.class);
  private final SearchIndexPort search = mock(SearchIndexPort.class);
  private final SearchIndexTarget target = new SearchIndexTarget("approved-index", 4);
  private final SearchIndexAuthorityApplicationManager authority = mock(SearchIndexAuthorityApplicationManager.class);
  private final CandidateLibraryQueryService library = mock(CandidateLibraryQueryService.class);
  private final EngagementLockQueryService locks = mock(EngagementLockQueryService.class);
  private final TalentSearchApplicationManager manager = new TalentSearchApplicationManager();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(manager, "facts", facts);
    ReflectionTestUtils.setField(manager, "search", search);
    ReflectionTestUtils.setField(manager, "authority", authority);
    when(authority.currentTarget()).thenReturn(target);
    ReflectionTestUtils.setField(manager, "library", library);
    ReflectionTestUtils.setField(manager, "locks", locks);
    ReflectionTestUtils.setField(manager, "authorization", mock(AuthorizationService.class));
    when(locks.activeLocks(any())).thenReturn(Map.of());
  }

  @Test
  void explainsEachCustomConditionWhenMainQueryIsAbsent() {
    var criteria = new SearchCriteria();
    criteria.setConditions(List.of("高压电工证", "低压电工证"));
    var results = search(criteria, List.of(document("both", "高压电工证 低压电工证")));
    assertThat(results.getFirst().getConditionMatches()).containsExactly(
        match("CUSTOM", "高压电工证", true, List.of("高压电工证")),
        match("CUSTOM", "低压电工证", true, List.of("低压电工证")));
  }

  @Test
  void readsEachDocumentTextAtMostOnceForHundredFullyMatchedResults() {
    var criteria = criteria("  JAVA\tSpring SQL  ", " cloud  MICROSERVICES\n上海 ");
    var documents = IntStream.range(0, 100)
        .mapToObj(index -> spy(document("candidate-" + index,
            "Java Spring SQL cloud microservices 上海")))
        .toList();

    var results = search(criteria, documents);

    assertThat(results).extracting(TalentSearchResultResponse::getCandidateId)
        .containsExactlyElementsOf(IntStream.range(0, 100)
            .mapToObj(index -> "candidate-" + index).toList());
    assertThat(results).allSatisfy(result -> assertThat(result.getConditionMatches())
        .containsExactly(
            match("KEYWORD", criteria.getQuery(), true, List.of(criteria.getQuery())),
            match("NATURAL_LEXICAL", criteria.getNaturalCondition(), true,
                List.of(criteria.getNaturalCondition()))));
    documents.forEach(document -> verify(document, atMost(1)).getSearchText());
  }

  @Test
  void requiresEveryTermAndRetainsOriginalQueriesOnlyForFullMatches() {
    var criteria = criteria("  JAVA\tSpring  ", " cloud SQL ");

    var results = search(criteria, List.of(
        document("both", "Spring Java cloud SQL"),
        document("keyword-only", "Java Spring cloud"),
        document("natural-only", "Java cloud SQL")));

    assertThat(results.get(0).getConditionMatches()).containsExactly(
        match("KEYWORD", criteria.getQuery(), true, List.of(criteria.getQuery())),
        match("NATURAL_LEXICAL", criteria.getNaturalCondition(), true,
            List.of(criteria.getNaturalCondition())));
    assertThat(results.get(1).getConditionMatches()).containsExactly(
        match("KEYWORD", criteria.getQuery(), true, List.of(criteria.getQuery())),
        match("NATURAL_LEXICAL", criteria.getNaturalCondition(), false, List.of()));
    assertThat(results.get(2).getConditionMatches()).containsExactly(
        match("KEYWORD", criteria.getQuery(), false, List.of()),
        match("NATURAL_LEXICAL", criteria.getNaturalCondition(), true,
            List.of(criteria.getNaturalCondition())));
  }

  @Test
  void reportsNoTextMatchForNullOrEmptyDocumentBodies() {
    var results = search(criteria("Java Spring", "cloud SQL"),
        List.of(document("null", null), document("empty", "")));

    assertThat(results).allSatisfy(result -> assertThat(result.getConditionMatches())
        .containsExactly(
            match("KEYWORD", "Java Spring", false, List.of()),
            match("NATURAL_LEXICAL", "cloud SQL", false, List.of())));
  }

  @Test
  void omitsNullEmptyAndWhitespaceQueries() {
    for (var criteria : List.of(criteria(null, ""), criteria(" \t\n ", null))) {
      var results = search(criteria,
          List.of(document("null", null), document("empty", ""), document("text", "Java")));
      assertThat(results).allSatisfy(result -> assertThat(result.getConditionMatches()).isEmpty());
    }
  }

  @Test
  void retainsRootCaseFoldingForLatinAndGreekWithoutChangingDefaultLocale() {
    var results = search(criteria("i σος", "ı"), List.of(document("unicode", "I ΣΟΣ")));

    assertThat(results.getFirst().getConditionMatches()).containsExactly(
        match("KEYWORD", "i σος", true, List.of("i σος")),
        match("NATURAL_LEXICAL", "ı", false, List.of()));
  }

  @Test
  void preservesStructuredConditionOrderAndRepeatedMatchingValues() {
    var criteria = criteria(null, null);
    criteria.setSkills(List.of("JAVA", "missing", "spring"));
    criteria.setEmployers(List.of("acme"));
    criteria.setTags(List.of("backend"));
    var document = document("structured", "irrelevant");
    document.setSkills(List.of("Java Spring", "java", "Java Spring"));
    document.setEmployers(List.of("Acme China", "ACME US", "Acme China"));
    document.setTags(List.of("Backend", "backend"));

    var results = search(criteria, List.of(document));

    assertThat(results.getFirst().getConditionMatches()).containsExactly(
        match("SKILL", "JAVA", true, List.of("Java Spring", "java", "Java Spring")),
        match("SKILL", "missing", false, List.of()),
        match("SKILL", "spring", true, List.of("Java Spring", "Java Spring")),
        match("EMPLOYER", "acme", true, List.of("Acme China", "ACME US", "Acme China")),
        match("TAG", "backend", true, List.of("Backend", "backend")));
  }

  private List<TalentSearchResultResponse> search(SearchCriteria criteria,
      List<SearchDocument> documents) {
    var candidateIds = documents.stream().map(SearchDocument::getCandidateId).toList();
    when(library.eligibleCandidateIds(candidateIds)).thenReturn(Set.copyOf(candidateIds));
    var currentFacts = documents.stream().collect(Collectors.toMap(
        SearchDocument::getResumeVersionId,
        document -> new CurrentFactContact(document.getFactSnapshotId(), null, null)));
    when(facts.currentFactContacts(documents.stream().map(SearchDocument::getResumeVersionId).toList()))
        .thenReturn(currentFacts);
    when(search.search(target, criteria, null, 100)).thenReturn(new IndexPage(documents, null, false));
    return manager.search(criteria, null, 100).getItems();
  }

  private static SearchCriteria criteria(String query, String naturalCondition) {
    var criteria = new SearchCriteria();
    criteria.setQuery(query);
    criteria.setNaturalCondition(naturalCondition);
    return criteria;
  }

  private static ConditionMatchResponse match(String code, String expected,
      boolean matched, List<String> values) {
    return new ConditionMatchResponse(code, expected, matched, values);
  }

  private static SearchDocument document(String candidateId, String text) {
    return new SearchDocument(candidateId, "resume-" + candidateId, "snapshot-" + candidateId,
        candidateId, "工程师", "上海", List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), BigDecimal.ONE, null, null, null, null,
        text, Instant.parse("2026-09-03T00:00:00Z"));
  }
}

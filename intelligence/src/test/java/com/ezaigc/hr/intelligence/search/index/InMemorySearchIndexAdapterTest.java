package com.ezaigc.hr.intelligence.search.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.intelligence.search.infrastructure.search.InMemorySearchIndexAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class InMemorySearchIndexAdapterTest {
  @Test
  void approvedPhysicalTargetSurvivesALateUnapprovedAliasActivation() {
    var approved = new SearchIndexTarget(index.activeIndex(), 4);
    index.upsertBatch(approved, List.of(doc("candidate", "Java", Instant.EPOCH)));
    var old = new SearchRebuildAttempt("late-terminal-run", 1);
    index.createRebuildTarget(old, "fixture-" + old.indexName());
    index.beginRebuild(old);
    index.activateRebuild(old);
    assertThat(index.activeIndex()).isEqualTo(old.indexName());
    assertThat(index.search(approved, new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("candidate");
  }

  @Test
  void cursorIsInvalidatedByAnApprovalGenerationEvenWhenThePhysicalNameIsTheSame() {
    var approved = new SearchIndexTarget(index.activeIndex(), 4);
    index.upsertBatch(approved, List.of(doc("a", "Java", Instant.EPOCH), doc("b", "Java", Instant.EPOCH)));
    var first = index.search(approved, new SearchCriteria(), null, 1);
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(approved.indexName(), 5),
        new SearchCriteria(), first.getNextCursor(), 1)).isInstanceOf(PlatformException.class);
  }

  @Test
  void memoryRestartRecreatesTheSameApprovedNameButRejectsEarlierIncarnationCursors() {
    var approved = new SearchIndexTarget("approved-memory-index", 4);
    index.ensureApprovedIndex(approved);
    index.upsertBatch(approved, List.of(doc("a", "Java", Instant.EPOCH), doc("b", "Java", Instant.EPOCH)));
    var first = index.search(approved, new SearchCriteria(), null, 1);
    var restarted = index();
    restarted.ensureApprovedIndex(approved);
    assertThatThrownBy(() -> restarted.search(approved, new SearchCriteria(), first.getNextCursor(), 1))
        .isInstanceOf(PlatformException.class);
    assertThat(restarted.search(approved, new SearchCriteria(), null, 1).getItems()).isEmpty();
  }
  @Test
  void customConditionsRequireBothCertificatesBeforePagingAndBindTheCursor() {
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(
        doc("a-single", "高压电工证 Java", Instant.EPOCH),
        doc("b-both", "高压电工证 低压电工证 Java", Instant.EPOCH),
        doc("c-both", "高压电工证 低压电工证 Java", Instant.EPOCH),
        doc("d-other", "低压电工证 Java", Instant.EPOCH)));
    var criteria = new SearchCriteria();
    criteria.setQuery("高压电工证");
    criteria.setConditions(List.of("低压电工证", "java"));
    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("b-both");
    assertThat(first.isHasMore()).isTrue();
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("c-both");
    criteria.setConditions(List.of("其他证书"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems()).isEmpty();
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1))
        .isInstanceOf(PlatformException.class);
    criteria.setConditions(List.of());
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems()).hasSize(3);
  }

  @Test
  void fixedDegreeChoicesMatchExplicitLegacyAliasesWithoutGuessingDiplomaOrSubstrings() {
    List<String> degrees = List.of("大专", "ASSOCIATE", "BACHELOR", "Bachelor", "bachelor",
        "硕士研究生", "MASTER", "PhD", "Doctorate", "Diploma", "本科在读", "Master Diploma");
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), IntStream.range(0, degrees.size()).mapToObj(i -> {
      var document = doc(String.format("candidate-%02d", i), "Java", Instant.EPOCH);
      document.setDegrees(List.of(degrees.get(i)));
      return document;
    }).toList());
    var selected = new SearchCriteria();
    selected.setDegrees(List.of("专科", "本科", "硕士", "博士"));
    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), selected, null, 4);
    assertThat(first.getItems()).extracting(d -> d.getDegrees().getFirst())
        .containsExactly("大专", "ASSOCIATE", "BACHELOR", "Bachelor");
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), selected, first.getNextCursor(), 10).getItems())
        .extracting(d -> d.getDegrees().getFirst())
        .containsExactly("bachelor", "硕士研究生", "MASTER", "PhD", "Doctorate");
    selected.setDegrees(List.of("BACHELOR"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), selected, null, 10).getItems()).hasSize(3);
    selected.setDegrees(List.of("Diploma"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), selected, null, 10).getItems())
        .extracting(d -> d.getDegrees().getFirst()).containsExactly("Diploma");
    selected.setDegrees(List.of());
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), selected, null, 20).getItems()).hasSize(12);
  }

  @Test
  void educationChoicesMatchAnyDegreeAndCombineWithOtherFilters() {
    var bachelor = doc("bachelor", "Java", Instant.EPOCH);
    bachelor.setDegrees(List.of("本科"));
    bachelor.setCity("上海");
    var master = doc("master", "Java", Instant.EPOCH);
    master.setDegrees(List.of("硕士"));
    master.setCity("北京");
    var other = doc("other", "Java", Instant.EPOCH);
    other.setDegrees(List.of("专科"));
    other.setCity("上海");
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(bachelor, master, other));
    var criteria = new SearchCriteria();
    criteria.setDegrees(List.of("本科", "硕士"));
    criteria.setCities(List.of("上海", "北京"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("bachelor", "master");
    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("bachelor");
    assertThat(first.isHasMore()).isTrue();
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("master");
    criteria.setSkills(List.of("Java", "Rust"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems()).isEmpty();
    criteria.setSkills(List.of());
    criteria.setCities(List.of("上海"));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("bachelor");
    criteria.setDegrees(List.of());
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("bachelor", "other");
  }

  @Test
  void placeholdersAndPermanentTombstonesHideOldFactsAndRejectLateWrites() {
    var published = doc("candidate", "old", Instant.EPOCH);
    var hidden = doc("candidate", "", Instant.EPOCH.plusSeconds(1));
    hidden.setFactSnapshotId(null);
    hidden.setProjectionVersion(2L << 32);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(published));
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(hidden, published));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems()).isEmpty();

    var parsed = doc("candidate", "latest", Instant.EPOCH.plusSeconds(2));
    parsed.setProjectionVersion((2L << 32) | 1);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(parsed));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getSearchText).containsExactly("latest");
    hidden.setProjectionVersion(Long.MAX_VALUE);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(hidden, parsed));
    var lateTombstoneVersion = doc("candidate", "must stay hidden", Instant.EPOCH.plusSeconds(3));
    lateTombstoneVersion.setProjectionVersion(Long.MAX_VALUE);
    lateTombstoneVersion.setCandidateVersion(Long.MAX_VALUE);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(lateTombstoneVersion));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems()).isEmpty();
  }
  private final InMemorySearchIndexAdapter index = index();

  @Test
  void lateOlderContentAndDuplicateDeliveryCannotReplaceThePublishedRevision() {
    var published = doc("candidate", "new", Instant.EPOCH);
    published.setProjectionVersion((2L << 32) | 1);
    var late = doc("candidate", "old", Instant.EPOCH.plusSeconds(100));
    late.setProjectionVersion((1L << 32) | Integer.MAX_VALUE);
    var duplicate = doc("candidate", "duplicate payload", Instant.EPOCH.plusSeconds(200));
    duplicate.setProjectionVersion(published.getProjectionVersion());
    published.setCandidateVersion(2);
    duplicate.setCandidateVersion(1);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(published));
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(late, duplicate));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getSearchText).containsExactly("new");
    duplicate.setSearchText("new contact");
    duplicate.setCandidateVersion(3);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(duplicate));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getSearchText).containsExactly("new contact");
    var corrected = doc("candidate", "corrected", Instant.EPOCH.minusSeconds(100));
    corrected.setProjectionVersion((2L << 32) | 2);
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(corrected));
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getSearchText).containsExactly("corrected");
  }

  @Test
  void newerAttemptFencesBothLateCreationAndLatePublication() {
    var first = new SearchRebuildAttempt("run-1", 1);
    var second = new SearchRebuildAttempt("run-1", 2);
    index.createRebuildTarget(first, "fixture-" + first.indexName());
    index.beginRebuild(first);
    index.upsertBatch(new SearchIndexTarget(first.indexName(), 0), List.of(doc("old", "Java", Instant.EPOCH)));
    index.activateRebuild(first);
    index.createRebuildTarget(second, "fixture-" + second.indexName());
    index.beginRebuild(second);
    assertThatThrownBy(() -> index.activateRebuild(first)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> index.beginRebuild(first)).isInstanceOf(IllegalStateException.class);
    index.upsertBatch(new SearchIndexTarget(second.indexName(), 0), List.of(doc("new", "Java", Instant.EPOCH)));
    index.activateRebuild(second);
    index.activateRebuild(second);
    assertThatThrownBy(() -> index.activateRebuild(first)).isInstanceOf(IllegalStateException.class);
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), new SearchCriteria(), null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("new");
  }

  @Test
  void publicationDuringCursorDecodeKeepsTheCurrentPageInItsCapturedIndex() {
    var firstAttempt = new SearchRebuildAttempt("page-run", 1);
    var secondAttempt = new SearchRebuildAttempt("page-run", 2);
    index.createRebuildTarget(firstAttempt, "fixture-" + firstAttempt.indexName());
    index.beginRebuild(firstAttempt);
    index.upsertBatch(new SearchIndexTarget(firstAttempt.indexName(), 0), List.of(
        doc("a-1", "Java", Instant.EPOCH),
        doc("a-2", "Java", Instant.EPOCH),
        doc("a-3", "Java", Instant.EPOCH)));
    index.activateRebuild(firstAttempt);
    index.createRebuildTarget(secondAttempt, "fixture-" + secondAttempt.indexName());
    index.beginRebuild(secondAttempt);
    index.upsertBatch(new SearchIndexTarget(secondAttempt.indexName(), 0), List.of(
        doc("b-1", "Java", Instant.EPOCH),
        doc("b-2", "Java", Instant.EPOCH),
        doc("b-3", "Java", Instant.EPOCH)));
    var signed = new SignedCursorCodec("search-single-page-test-key-32-bytes");
    List<String> decodedScopes = new ArrayList<>();
    ReflectionTestUtils.setField(index, "cursorCodec", new CursorCodec() {
      private boolean published;

      @Override
      public String encode(String scope, List<String> values) {
        return signed.encode(scope, values);
      }

      @Override
      public List<String> decode(String token, String expectedScope, int expectedValueCount) {
        List<String> values = signed.decode(token, expectedScope, expectedValueCount);
        decodedScopes.add(expectedScope);
        if (!published) {
          published = true;
          index.activateRebuild(secondAttempt);
        }
        return values;
      }
    });
    var criteria = new SearchCriteria();
    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("a-1");
    assertThat(first.isHasMore()).isTrue();
    assertThat(first.getNextCursor()).isNotBlank();

    var second = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1);
    assertThat(second.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("a-2");
    assertThat(second.isHasMore()).isTrue();
    assertThat(second.getNextCursor()).isNotBlank();
    assertThat(decodedScopes).hasSize(1);
    assertThat(signed.decode(second.getNextCursor(), decodedScopes.getFirst(), 2))
        .containsExactly(Instant.EPOCH.toString(), "a-2");
    assertThat(index.activeIndex()).isEqualTo(secondAttempt.indexName());
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, second.getNextCursor(), 1))
        .isInstanceOfSatisfying(PlatformException.class, e -> {
          assertThat(e.code()).isEqualTo("SEARCH_CURSOR_SCOPE_MISMATCH");
          assertThat(e.status().value()).isEqualTo(409);
          assertThat(e.recoverable()).isTrue();
        });
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 10).getItems())
        .extracting(SearchDocument::getCandidateId).containsExactly("b-1", "b-2", "b-3");
  }

  private static InMemorySearchIndexAdapter index() {
    InMemorySearchIndexAdapter index = new InMemorySearchIndexAdapter();
    ReflectionTestUtils.setField(index, "json", JsonMapper.builder().build());
    ReflectionTestUtils.setField(
        index, "cursorCodec", new SignedCursorCodec("search-index-test-signing-key-32-bytes"));
    ReflectionTestUtils.invokeMethod(index, "initializeIndex");
    return index;
  }

  @Test
  void combinesStructuredAndLexicalConditionsAndUsesStableSearchAfter() {
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0),
        List.of(
            doc("candidate-b", "Java 支付平台", Instant.parse("2026-01-02T00:00:00Z")),
            doc("candidate-a", "Java 支付平台", Instant.parse("2026-01-02T00:00:00Z"))));
    SearchCriteria criteria = criteria("Java", List.of("Java"));
    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems())
        .extracting(SearchDocument::getCandidateId)
        .containsExactly("candidate-a");
    assertThat(first.isHasMore()).isTrue();
    assertThat(index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1).getItems())
        .extracting(SearchDocument::getCandidateId)
        .containsExactly("candidate-b");
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria("Kotlin", List.of()), first.getNextCursor(), 1))
        .isInstanceOfSatisfying(
            PlatformException.class,
            e -> assertThat(e.code()).isEqualTo("SEARCH_CURSOR_SCOPE_MISMATCH"));
  }

  @Test
  void workYearsUpperBoundIsExclusiveAndChangesCursorScope() {
    var below = doc("below", "Java", Instant.EPOCH);
    below.setWorkYears(new BigDecimal("2.99"));
    var lower = doc("lower", "Java", Instant.EPOCH.minusSeconds(1));
    lower.setWorkYears(new BigDecimal("1"));
    var boundary = doc("boundary", "Java", Instant.EPOCH);
    boundary.setWorkYears(new BigDecimal("3"));
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0), List.of(below, lower, boundary));
    var criteria = new SearchCriteria();
    criteria.setMaxWorkYears(new BigDecimal("3"));

    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("below");

    criteria.setMaxWorkYears(new BigDecimal("5"));
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void candidateIdsRestrictSearchAndChangeCursorScope() {
    index.upsertBatch(new SearchIndexTarget(index.activeIndex(), 0),
        List.of(
            doc("allowed-a", "Java", Instant.EPOCH),
            doc("allowed-b", "Java", Instant.EPOCH.minusSeconds(1)),
            doc("excluded", "Java", Instant.EPOCH.plusSeconds(1))));
    var criteria = new SearchCriteria();
    criteria.setCandidateIds(List.of("allowed-a", "allowed-b"));

    var first = index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, null, 1);
    assertThat(first.getItems()).extracting(SearchDocument::getCandidateId).containsExactly("allowed-a");

    criteria.setCandidateIds(List.of("allowed-a", "allowed-b", "excluded"));
    assertThatThrownBy(() -> index.search(new SearchIndexTarget(index.activeIndex(), 0), criteria, first.getNextCursor(), 1))
        .isInstanceOf(PlatformException.class);
  }

  private static SearchDocument doc(String candidate, String text, Instant updated) {
    var document = new SearchDocument(
        candidate,
        "resume-" + candidate,
        "fact-" + candidate,
        "候选人",
        "Java工程师",
        "上海",
        List.of("示例公司"),
        List.of("Java工程师"),
        List.of("支付平台"),
        List.of("Java"),
        List.of(),
        List.of("英语"),
        List.of("本科"),
        List.of("金融"),
        List.of("平台开发"),
        new BigDecimal("5.00"),
        null,
        null,
        null,
        null,
        text,
        updated);
    document.setProjectionVersion((1L << 32) | 1);
    return document;
  }

  private static SearchCriteria criteria(String q, List<String> skills) {
    return new SearchCriteria(
        q,
        skills,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of("上海"),
        List.of(),
        List.of(),
        new BigDecimal("3"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "支付");
  }
}

package com.ezaigc.hr.intelligence.search.infrastructure.search;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_PROJECTION_PAGE_SIZE;

import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.IndexPage;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchRebuildAttempt;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchIndexTarget;
import com.ezaigc.hr.talent.api.DegreeQueryAliases;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "hr.search.mode", havingValue = "memory", matchIfMissing = true)
public class InMemorySearchIndexAdapter implements SearchIndexPort {
  private final Map<String, Map<String, SearchDocument>> indices = new ConcurrentHashMap<>();
  private final Map<String, Integer> rebuildAttempts = new ConcurrentHashMap<>();
  private final Map<String, String> resourceOwners = new ConcurrentHashMap<>();
  private final String incarnation = UUID.randomUUID().toString();
  @Resource private ObjectMapper json;
  @Resource private CursorCodec cursorCodec;
  /** 是否处于启用状态。 */
  private volatile String active = "memory-active";

  @PostConstruct
  void initializeIndex() {
    ensureActiveIndex();
  }

  @Override
  public boolean durable() {
    return false;
  }

  /** 非持久开发模式初始空容器，无外部创建请求。 */
  private void ensureActiveIndex() {
    indices.computeIfAbsent(active, k -> new ConcurrentHashMap<>());
  }

  @Override
  public String activeIndex() {
    return active;
  }

  @Override
  public boolean isManagedBootstrap(String indexName) {
    return "memory-active".equals(indexName) && indices.containsKey(indexName);
  }

  @Override
  public void ensureApprovedIndex(SearchIndexTarget target) {
    requireLive(target);
    String indexName = target.indexName();
    // 非持久开发模式可重建同一个已批准目标的空容器，随后既有检查点从头重放。
    if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("approved index required");
    indices.computeIfAbsent(indexName, ignored -> new ConcurrentHashMap<>());
  }

  @Override
  public String resourceScope() { return "memory"; }

  @Override
  public synchronized void createBootstrap(String indexName, String resourceId) {
    createOwned(indexName, resourceId);
    active = indexName;
  }

  @Override
  public synchronized void createRebuildTarget(SearchRebuildAttempt attempt, String resourceId) {
    createOwned(attempt.indexName(), resourceId);
  }

  private void createOwned(String indexName, String resourceId) {
    if (indices.putIfAbsent(indexName, new ConcurrentHashMap<>()) != null) {
      throw new IllegalStateException("search index already exists");
    }
    resourceOwners.put(indexName, resourceId);
  }

  @Override
  public synchronized void retireIndex(String indexName, String resourceId) {
    String owner = resourceOwners.get(indexName);
    // 开发模式重启后只有本地空容器，持久 ledger 仍决定目标能否被回收。
    if (owner != null && !owner.equals(resourceId)) throw new IllegalStateException("search resource owner mismatch");
    indices.remove(indexName);
    resourceOwners.remove(indexName);
  }

  @Override
  public synchronized boolean adoptIndex(String indexName, String resourceId) {
    if (!indices.containsKey(indexName)) return false;
    String existing = resourceOwners.putIfAbsent(indexName, resourceId);
    if (existing != null && !existing.equals(resourceId)) throw new IllegalStateException("search resource owner mismatch");
    return true;
  }

  @Override
  public void prepareRebuild(SearchRebuildAttempt attempt) {
    if (!indices.containsKey(attempt.indexName())) throw new IllegalStateException("rebuild not started");
  }

  @Override
  public IndexPage search(SearchIndexTarget target, SearchCriteria criteria, String cursor, int limit) {
    String fingerprint = fingerprint(criteria);
    String indexName = target.indexName();
    ensureApprovedIndex(target);
    String scope = CursorScopes.scope("search.memory", fingerprint, indexName, target.generation(), incarnation);
    Cursor after = cursor(cursor, scope);
    List<SearchDocument> all =
        indices.getOrDefault(indexName, Map.of()).values().stream()
            .filter(v -> v.getFactSnapshotId() != null)
            .filter(v -> matches(v, criteria))
            .sorted(
                Comparator.comparing(SearchDocument::getUpdateTime)
                    .reversed()
                    .thenComparing(SearchDocument::getCandidateId))
            .filter(
                v ->
                    after == null
                        || v.getUpdateTime().isBefore(after.updateTime())
                        || (v.getUpdateTime().equals(after.updateTime())
                            && v.getCandidateId().compareTo(after.candidateId()) > 0))
            .toList();
    boolean more = all.size() > limit;
    List<SearchDocument> page = all.stream().limit(limit).toList();
    String next = more && !page.isEmpty() ? cursor(page.getLast(), scope) : null;
    return new IndexPage(page, next, more);
  }

  @Override
  public synchronized void beginRebuild(SearchRebuildAttempt attempt) {
    int current = rebuildAttempts.getOrDefault(attempt.runId(), 0);
    if (current > attempt.attempt()) throw new IllegalStateException("search rebuild attempt superseded");
    if (!indices.containsKey(attempt.indexName())) throw new IllegalStateException("rebuild not started");
    rebuildAttempts.put(attempt.runId(), attempt.attempt());
  }

  @Override
  public void upsertBatch(SearchIndexTarget indexTarget, List<SearchDocument> documents) {
    requireLive(indexTarget);
    Map<String, SearchDocument> target = indices.get(indexTarget.indexName());
    if (target == null) throw new IllegalStateException("rebuild not started");
    if (documents == null || documents.size() > MAX_PROJECTION_PAGE_SIZE) {
      throw new IllegalArgumentException("search projection batch is required and bounded");
    }
    for (SearchDocument document : documents) {
      if (document == null || document.getCandidateId() == null
          || document.getCandidateId().isBlank() || document.getProjectionVersion() < 1) {
        throw new IllegalArgumentException("search index document candidate ID and positive version are required");
      }
    }
    documents.forEach(document -> target.merge(document.getCandidateId(), document,
        (current, incoming) -> newer(incoming, current)
            ? incoming : current));
  }

  private static void requireLive(SearchIndexTarget target) {
    if (target.remainingTimeout(Duration.ofMinutes(2)).isZero()) {
      throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INDEX_UNAVAILABLE",
          "搜索请求期限已届满，请重试", true);
    }
  }

  private static boolean newer(SearchDocument incoming, SearchDocument current) {
    return current.getProjectionVersion() != Long.MAX_VALUE
        && (incoming.getProjectionVersion() > current.getProjectionVersion()
            || incoming.getProjectionVersion() == current.getProjectionVersion()
                && incoming.getCandidateVersion() > current.getCandidateVersion());
  }

  /** 激活重建。 */
  @Override
  public synchronized void activateRebuild(SearchRebuildAttempt attempt) {
    if (!Integer.valueOf(attempt.attempt()).equals(rebuildAttempts.get(attempt.runId()))) {
      throw new IllegalStateException("search rebuild attempt superseded or not started");
    }
    if (!indices.containsKey(attempt.indexName())) throw new IllegalStateException("rebuild not started");
    active = attempt.indexName();
  }

  private static boolean matches(SearchDocument d, SearchCriteria c) {
    return (c.getCandidateIds().isEmpty() || c.getCandidateIds().contains(d.getCandidateId()))
        && containsText(d, c.getQuery())
        && c.getConditions().stream().allMatch(condition -> containsText(d, condition))
        && all(d.getSkills(), c.getSkills())
        && all(d.getEmployers(), c.getEmployers())
        && all(d.getRoles(), c.getRoles())
        && all(d.getProjects(), c.getProjects())
        && all(d.getCertificates(), c.getCertificates())
        && all(d.getLanguages(), c.getLanguages())
        && any(d.getDegrees(), DegreeQueryAliases.expand(c.getDegrees()))
        && all(d.getIndustries(), c.getIndustries())
        && all(d.getTags(), c.getTags())
        && one(d.getCity(), c.getCities())
        && (c.getMinWorkYears() == null
            || d.getWorkYears() != null && d.getWorkYears().compareTo(c.getMinWorkYears()) >= 0)
        && (c.getMaxWorkYears() == null
            || d.getWorkYears() != null && d.getWorkYears().compareTo(c.getMaxWorkYears()) < 0)
        && eq(d.getSourceChannel(), c.getSourceChannel())
        && contains(d.getIntention(), c.getIntention())
        && eq(d.getGender(), c.getGender())
        && (c.getMinAge() == null || d.getAge() != null && d.getAge() >= c.getMinAge())
        && (c.getMaxAge() == null || d.getAge() != null && d.getAge() <= c.getMaxAge())
        && (c.getUpdatedFrom() == null || !d.getUpdateTime().isBefore(c.getUpdatedFrom()))
        && (c.getUpdatedTo() == null || !d.getUpdateTime().isAfter(c.getUpdatedTo()))
        && containsText(d, c.getNaturalCondition());
  }

  private static boolean containsText(SearchDocument d, String q) {
    if (q == null || q.isBlank()) return true;
    String text = norm(d.getSearchText());
    for (String term : q.strip().split("\\s+")) if (!text.contains(norm(term))) return false;
    return true;
  }

  private static boolean all(List<String> actual, List<String> expected) {
    return expected.stream()
        .allMatch(v -> actual.stream().anyMatch(a -> norm(a).contains(norm(v))));
  }

  private static boolean any(List<String> actual, List<String> expected) {
    return expected.isEmpty() || expected.stream().anyMatch(actual::contains);
  }

  private static boolean one(String actual, List<String> expected) {
    return expected.isEmpty() || expected.contains(actual);
  }

  private static boolean contains(String a, String b) {
    return b == null || b.isBlank() || norm(a).contains(norm(b));
  }

  private static boolean eq(String a, String b) {
    return b == null || b.isBlank() || norm(a).equals(norm(b));
  }

  private static String norm(String v) {
    return v == null ? "" : v.strip().toLowerCase(Locale.ROOT);
  }

  private String cursor(SearchDocument document, String scope) {
    return cursorCodec.encode(
        scope, List.of(document.getUpdateTime().toString(), document.getCandidateId()));
  }

  private Cursor cursor(String value, String scope) {
    if (value == null || value.isBlank()) return null;
    try {
      List<String> values = cursorCodec.decode(value, scope, 2);
      return new Cursor(Instant.parse(values.get(0)), values.get(1));
    } catch (Exception ex) {
      throw new PlatformException(
          HttpStatus.CONFLICT, "SEARCH_CURSOR_SCOPE_MISMATCH", "搜索游标不属于当前筛选条件或已失效", true);
    }
  }

  private String fingerprint(SearchCriteria c) {
    try {
      return Sha256Utils.hashUtf8(json.writeValueAsString(c));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class Cursor {
    private final Instant updateTime;
    private final String candidateId;
  }
}

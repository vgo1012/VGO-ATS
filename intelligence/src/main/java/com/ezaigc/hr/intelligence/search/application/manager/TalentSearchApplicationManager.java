package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_SEARCH_PAGE_SIZE;

import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.CursorCount;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import com.ezaigc.hr.intelligence.search.enums.TalentSearchScope;
import com.ezaigc.hr.intelligence.search.domain.dto.SearchDocument;
import com.ezaigc.hr.talent.api.DegreeQueryAliases;
import com.ezaigc.hr.intelligence.search.domain.response.ConditionMatchResponse;
import com.ezaigc.hr.intelligence.search.domain.response.TalentSearchPageResponse;
import com.ezaigc.hr.intelligence.search.domain.response.TalentSearchResultResponse;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService;
import com.ezaigc.hr.recruiting.recruitment.api.MyTaskCandidateQuery;
import com.ezaigc.hr.recruiting.recruitment.api.EngagementLockQueryService.LockSummary;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.CandidateLibraryQueryService;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.CurrentFactContact;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/** 人才搜索应用服务，负责用例编排、权限校验和事务边界。 */
@Component
public class TalentSearchApplicationManager {

  /** 补齐结果与探测下一页共享预算，限制索引陈旧时单次在线请求的回查工作量。 */
  private static final int MAX_PROVIDER_CALLS = 8;

  @Resource private ResumeFactQueryService facts;
  @Resource private EngagementLockQueryService locks;
  @Resource private AuthorizationService authorization;
  @Resource private SearchIndexPort search;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private CandidateLibraryQueryService library;
  @Resource private MyTaskCandidateQuery myTasks;

  private void authorizeCriteria(SearchCriteria criteria) {
    TalentSearchScope requestedScope = TalentSearchScope.fromCode(criteria.getScope());
    if (criteria.getPoolStage()!=null && requestedScope!=TalentSearchScope.PERSONAL_POOL)
      throw PlatformExceptions.badRequest("INVALID_POOL_STAGE","简历分类只适用于个人池");
    if (requestedScope != TalentSearchScope.SHARED) {
      authorization.requirePermission("talent.read");
    }
    if (requestedScope == TalentSearchScope.MY_TASKS) {
      authorization.requirePermission("recruitment.manage");
    }
    if (requestedScope != TalentSearchScope.SHARED
        || criteria.getJdId() != null) {
      var actor = authorization.currentActor();
      criteria.setCursorActor(CursorScopes.scope(
          "talent.search", actor.getUserId(), actor.hasCompanyWideScope(), actor.getManagedUserIds()));
    }
  }

  /** 每次仅统计一个有界索引批次；调用方续扫完成后才能将累计值作为总数。 */
  public CursorCount count(SearchCriteria criteria, String cursor) {
    authorizeCriteria(criteria);
    var target = authority.currentTarget();
    var batch = search.search(target, criteria, cursor, MAX_SEARCH_PAGE_SIZE);
    String next = batch.isHasMore() ? requireAdvanced(cursor, batch.getNextCursor()) : null;
    return new CursorCount(fresh(criteria, batch.getItems()).size(), next, batch.isHasMore());
  }

  public TalentSearchPageResponse search(SearchCriteria criteria, String cursor, int limit) {
    authorizeCriteria(criteria);
    // 补页/探测页均使用一次公开请求开始时的同一批准快照。
    var target = authority.currentTarget();
    int boundedLimit = PageLimits.resolve(limit, PageLimits.DEFAULT, MAX_SEARCH_PAGE_SIZE);
    List<FreshHit> collected = new ArrayList<>(boundedLimit);
    String scanCursor = cursor;
    for (int call = 0; call < MAX_PROVIDER_CALLS; call++) {
      boolean probing = collected.size() == boundedLimit;
      int needed = probing ? MAX_SEARCH_PAGE_SIZE : boundedLimit - collected.size();
      var providerPage = search.search(target, criteria, scanCursor, needed);
      var freshHits = fresh(criteria, providerPage.getItems());
      if (probing && !freshHits.isEmpty()) {
        // 探测页存在有效候选人时不能跨过它；下次请求从该页之前重新读取并重新鉴权。
        return response(criteria, collected, scanCursor, true, false);
      }
      if (!probing) collected.addAll(freshHits);
      if (!providerPage.isHasMore()) {
        return response(criteria, collected, null, false, false);
      }
      // 已证明全为空的探测页可以跳过，避免下次请求重复扫描同一段陈旧投影。
      scanCursor = requireAdvanced(scanCursor, providerPage.getNextCursor());
    }
    return response(criteria, collected, scanCursor, true, true);
  }

  private List<FreshHit> fresh(SearchCriteria criteria, List<SearchDocument> hits) {
    var candidateIds = hits.stream().map(SearchDocument::getCandidateId).toList();
    var eligible = switch (TalentSearchScope.fromCode(criteria.getScope())) {
      case PERSONAL_POOL -> criteria.getPoolStage()!=null
          ? library.personalPoolCandidateIds(candidateIds,criteria.getJdId(),criteria.getPoolStage())
          : criteria.getJdId() == null
          ? library.personalPoolCandidateIds(candidateIds)
          : library.personalPoolCandidateIds(candidateIds, criteria.getJdId());
      case MY_TASKS -> myTasks.candidateIds(candidateIds, criteria.getJdId());
      case SHARED -> criteria.getJdId() == null
          ? library.eligibleCandidateIds(candidateIds)
          : library.eligibleCandidateIds(candidateIds, criteria.getJdId());
    };
    // 已被实时准入排除的命中无需再读取事实和联系方式，尤其是补页和探测页。
    var eligibleHits = hits.stream()
        .filter(hit -> eligible.contains(hit.getCandidateId()))
        .toList();
    if (eligibleHits.isEmpty()) return List.of();
    Map<String, CurrentFactContact> current =
        facts.currentFactContacts(
            eligibleHits.stream().map(SearchDocument::getResumeVersionId).toList());
    return eligibleHits.stream()
        .map(hit -> new FreshHit(hit, current.get(hit.getResumeVersionId())))
        .filter(
            hit ->
                hit.contact() != null
                    && Objects.equals(
                        hit.document().getFactSnapshotId(), hit.contact().snapshotId()))
        .toList();
  }

  private TalentSearchPageResponse response(
      SearchCriteria criteria, List<FreshHit> hits, String nextCursor, boolean hasMore,
      boolean scanIncomplete) {
    Map<String, LockSummary> activeLocks =
        locks.activeLocks(
            hits.stream().map(hit -> hit.document().getCandidateId()).toList());
    List<String> keywordTerms = terms(criteria.getQuery());
    List<String> naturalTerms = terms(criteria.getNaturalCondition());
    return new TalentSearchPageResponse(
        hits.stream()
            .map(
                hit ->
                    response(
                        hit, criteria, activeLocks.get(hit.document().getCandidateId()),
                        keywordTerms, naturalTerms))
            .toList(),
        nextCursor,
        hasMore,
        scanIncomplete);
  }

  private TalentSearchResultResponse response(
      FreshHit freshHit, SearchCriteria criteria, LockSummary engagementLock,
      List<String> keywordTerms, List<String> naturalTerms) {
    SearchDocument hit = freshHit.document();
    return new TalentSearchResultResponse(
        hit.getCandidateId(),
        hit.getResumeVersionId(),
        hit.getFactSnapshotId(),
        hit.getFullName(),
        freshHit.contact().email(),
        freshHit.contact().phone(),
        hit.getCurrentRole(),
        hit.getCity(),
        hit.getAge(),
        hit.getGender(),
        hit.getIntention(),
        hit.getSourceChannel(),
        hit.getUpdateTime(),
        hit.getSkills(),
        hit.getEmployers(),
        hit.getRoles(),
        hit.getProjects(),
        hit.getCertificates(),
        hit.getLanguages(),
        hit.getDegrees(),
        hit.getIndustries(),
        hit.getTags(),
        hit.getWorkYears(),
        engagementLock,
        explain(hit, criteria, keywordTerms, naturalTerms),
        List.of(),
        List.of(),
        List.of());
  }

  @Getter(AccessLevel.PRIVATE)
  @Accessors(fluent = true)
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  private static final class FreshHit {
    private final SearchDocument document;
    private final CurrentFactContact contact;
  }

  private static String requireAdvanced(String previous, String next) {
    if (next == null || next.isBlank() || next.equals(previous)) {
      throw new IllegalStateException("search provider did not advance its cursor");
    }
    return next;
  }

  private static List<ConditionMatchResponse> explain(
      SearchDocument document, SearchCriteria criteria,
      List<String> keywordTerms, List<String> naturalTerms) {
    List<ConditionMatchResponse> out = new ArrayList<>();
    String text = keywordTerms.isEmpty() && naturalTerms.isEmpty() && criteria.getConditions().isEmpty() ? null : document.getSearchText();
    String normalizedText = text == null ? null : text.toLowerCase(Locale.ROOT);
    addText(out, "KEYWORD", criteria.getQuery(), normalizedText, keywordTerms);
    for (String condition : criteria.getConditions()) {
      addText(out, "CUSTOM", condition, normalizedText, terms(condition));
    }
    addAll(out, "SKILL", criteria.getSkills(), document.getSkills());
    addAll(out, "EMPLOYER", criteria.getEmployers(), document.getEmployers());
    addAll(out, "ROLE", criteria.getRoles(), document.getRoles());
    addAll(out, "PROJECT", criteria.getProjects(), document.getProjects());
    addAll(out, "CERTIFICATE", criteria.getCertificates(), document.getCertificates());
    addAll(out, "LANGUAGE", criteria.getLanguages(), document.getLanguages());
    addAny(out, "DEGREE", criteria.getDegrees(),
        document.getDegrees(),
        DegreeQueryAliases.expand(criteria.getDegrees()));
    addAny(out, "CITY", criteria.getCities(), nullable(document.getCity()));
    addAll(out, "INDUSTRY", criteria.getIndustries(), document.getIndustries());
    addAll(out, "TAG", criteria.getTags(), document.getTags());
    add(
        out,
        "SOURCE_CHANNEL",
        criteria.getSourceChannel(),
        same(document.getSourceChannel(), criteria.getSourceChannel()),
        nullable(document.getSourceChannel()));
    add(
        out,
        "INTENTION",
        criteria.getIntention(),
        contains(document.getIntention(), criteria.getIntention()),
        nullable(document.getIntention()));
    add(
        out,
        "GENDER",
        criteria.getGender(),
        same(document.getGender(), criteria.getGender()),
        nullable(document.getGender()));
    if (criteria.getMinAge() != null)
      out.add(
          new ConditionMatchResponse(
              "MIN_AGE",
              String.valueOf(criteria.getMinAge()),
              document.getAge() != null && document.getAge() >= criteria.getMinAge(),
              document.getAge() == null ? List.of() : List.of(String.valueOf(document.getAge()))));
    if (criteria.getMaxAge() != null)
      out.add(
          new ConditionMatchResponse(
              "MAX_AGE",
              String.valueOf(criteria.getMaxAge()),
              document.getAge() != null && document.getAge() <= criteria.getMaxAge(),
              document.getAge() == null ? List.of() : List.of(String.valueOf(document.getAge()))));
    if (criteria.getMinWorkYears() != null)
      out.add(
          new ConditionMatchResponse(
              "MIN_WORK_YEARS",
              criteria.getMinWorkYears().toPlainString(),
              document.getWorkYears() != null
                  && document.getWorkYears().compareTo(criteria.getMinWorkYears()) >= 0,
              document.getWorkYears() == null
                  ? List.of()
                  : List.of(document.getWorkYears().toPlainString())));
    addText(out, "NATURAL_LEXICAL", criteria.getNaturalCondition(), normalizedText, naturalTerms);
    return List.copyOf(out);
  }

  private static void addAny(List<ConditionMatchResponse> out, String code,
      List<String> expected, List<String> actual) {
    addAny(out, code, expected, actual, expected);
  }

  private static void addAny(List<ConditionMatchResponse> out, String code,
      List<String> expected, List<String> actual, List<String> matchingValues) {
    if (expected.isEmpty()) return;
    List<String> matched = actual.stream().filter(matchingValues::contains).toList();
    out.add(new ConditionMatchResponse(code, String.join(" / ", expected) + "（任一）",
        !matched.isEmpty(), matched));
  }

  private static void addAll(
      List<ConditionMatchResponse> out,
      String code,
      List<String> expected,
      List<String> actual) {
    for (String value : expected) {
      List<String> matched = actual.stream().filter(item -> contains(item, value)).toList();
      out.add(
          new ConditionMatchResponse(
              code,
              value,
              !matched.isEmpty(),
              matched));
    }
  }

  private static void add(
      List<ConditionMatchResponse> out,
      String code,
      String expected,
      boolean matched,
      List<String> values) {
    if (expected != null && !expected.isBlank())
      out.add(new ConditionMatchResponse(code, expected, matched, List.copyOf(values)));
  }

  private static boolean contains(String actual, String expected) {
    return actual != null
        && expected != null
        && actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
  }

  private static boolean same(String actual, String expected) {
    return expected == null
        || expected.isBlank()
        || actual != null && actual.equalsIgnoreCase(expected);
  }

  private static List<String> nullable(String value) {
    return value == null || value.isBlank() ? List.of() : List.of(value);
  }

  private static List<String> terms(String query) {
    return query == null || query.isBlank()
        ? List.of()
        : Arrays.stream(query.strip().split("\\s+"))
            .map(value -> value.toLowerCase(Locale.ROOT)).toList();
  }

  private static void addText(
      List<ConditionMatchResponse> out, String code, String query,
      String normalizedText, List<String> terms) {
    if (query == null || query.isBlank()) return;
    boolean matched = normalizedText != null && terms.stream().allMatch(normalizedText::contains);
    add(out, code, query, matched, matched ? List.of(query) : List.of());
  }
}

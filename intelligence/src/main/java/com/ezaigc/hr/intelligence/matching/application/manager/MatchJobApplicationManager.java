package com.ezaigc.hr.intelligence.matching.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorPages;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.AuthorizationTarget;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.intelligence.matching.domain.dto.MatchJobQuery;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobBasisRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchJobRow;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobBasisResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResultSummaryResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchJobResultResponse;
import com.ezaigc.hr.intelligence.matching.domain.response.MatchResultResponse;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 将同岗位的执行记录投影为工作入口，不修改评分、招聘决定或审计事实。 */
@Service
@Transactional(readOnly = true)
public class MatchJobApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private MatchRunPersistenceService runs;
  @Resource private CursorCodec cursorCodec;
  @Resource private ObjectMapper json;

  public CursorPage<MatchJobResponse> jobs(String jdVersionId, String cursor, int limit) {
    var query = query(jdVersionId, limit);
    // 精确岗位按版本聚合最多一行，首页无需生成游标，也无需扫描分页修订摘要。
    if (jdVersionId != null && !jdVersionId.isBlank() && (cursor == null || cursor.isBlank())) {
      return new CursorPage<>(runs.findJobs(query).stream().limit(1).map(MatchJobResponse::from).toList(),
          null, false);
    }
    String scope = scope("matching.jobs", query);
    position(query, cursor, scope);
    return CursorPages.page(runs.findJobs(query), query.getLimit() - 1, cursorCodec, scope,
        MatchJobResponse::from, MatchJobRow::getLastRunAt, MatchJobRow::getJdVersionId);
  }

  public CursorPage<MatchJobBasisResponse> bases(String jdVersionId, String cursor, int limit) {
    var query = query(jdVersionId, limit);
    String scope = scope("matching.job.bases", query);
    position(query, cursor, scope);
    return CursorPages.page(runs.findJobBases(query), query.getLimit() - 1, cursorCodec, scope,
        row -> MatchJobBasisResponse.from(row, json), MatchJobBasisRow::getLastRunAt, MatchJobBasisRow::getBasisRunId);
  }

  public CursorPage<MatchJobResultResponse> results(String jdVersionId, String basisRunId,
      String cursor, int limit) {
    var query = resultQuery(jdVersionId, basisRunId, limit);
    String scope = scope("matching.job.results", query);
    if (cursor != null && !cursor.isBlank()) {
      try {
        long rank = Long.parseLong(cursorCodec.decode(cursor, scope, 1).getFirst());
        if (rank < 1) throw new IllegalArgumentException("invalid rank");
        query.setAfterRank(rank);
      } catch (IllegalArgumentException invalid) {
        throw PlatformExceptions.badRequest("CURSOR_INVALID", "评分结果已变化或分页游标无效，请刷新列表");
      }
    }
    var rows = runs.findJobResults(query);
    int pageLimit = query.getLimit() - 1;
    boolean hasMore = rows.size() > pageLimit;
    var page = rows.stream().limit(pageLimit).toList();
    String next = hasMore ? cursorCodec.encode(scope, List.of(Long.toString(page.getLast().getWorkspaceRank()))) : null;
    return new CursorPage<>(page.stream().map(v -> new MatchJobResultResponse(v.getMatchRunId(),
        v.getUpdateTime(), new MatchResultResponse(v.getId(), v.getBaseEvaluationId(), v.getCandidateId(),
        v.getResumeVersionId(), v.getProfessionalScore(), v.getBonusScore(), v.getReferenceScore(),
        v.getMustTier(), Math.toIntExact(v.getWorkspaceRank()), json.readTree(v.getResultJson())))).toList(), next, hasMore);
  }

  public MatchJobResultSummaryResponse resultsSummary(String jdVersionId, String basisRunId) {
    return MatchJobResultSummaryResponse.from(runs.findJobResultsSummary(resultQuery(jdVersionId, basisRunId, 20)));
  }

  private MatchJobQuery resultQuery(String jdVersionId, String basisRunId, int limit) {
    var query = query(jdVersionId, limit);
    var run = runs.findRun(basisRunId);
    if (run == null) throw PlatformExceptions.notFound("MATCH_RUN_NOT_FOUND", "评分依据运行不存在");
    try {
      authorization.requireAccess("matching.run", new AuthorizationTarget("MATCH_RUN", run.getId(),
          run.getOwnerUserId(), run.getOwnerDepartmentId(), Set.of()));
    } catch (PlatformException denied) {
      query.setBasisRunId(basisRunId);
      if (runs.countVisibleJobBasis(query) == 0) throw denied;
    }
    if (!jdVersionId.equals(run.getJdVersionId())) {
      throw PlatformExceptions.unprocessable("MATCH_JOB_BASIS_MISMATCH", "评分依据不属于当前岗位版本");
    }
    query.setBasisRunId(basisRunId);
    return query;
  }

  private MatchJobQuery query(String jdVersionId, int requestedLimit) {
    authorization.requirePermission("matching.run");
    CurrentActorSnapshot actor = authorization.currentActor();
    var query = new MatchJobQuery();
    query.setActorId(actor.getUserId());
    query.setCompanyScope(actor.hasCompanyWideScope());
    query.setManagedUserIds(actor.getOperationalManagedUserIds());
    query.setDepartmentIds(actor.visibleDepartmentIdsFor("MATCH_RUN"));
    query.setJdVersionId(jdVersionId);
    query.setLimit(PageLimits.resolve(requestedLimit) + 1);
    return query;
  }

  private String scope(String name, MatchJobQuery query) {
    return CursorScopes.scope(name, query.getActorId(), query.isCompanyScope(),
        query.getManagedUserIds().stream().sorted().toList(), query.getDepartmentIds().stream().sorted().toList(),
        query.getJdVersionId(), query.getBasisRunId(), runs.findJobRevision(query));
  }

  private void position(MatchJobQuery query, String cursor, String scope) {
    StableCursor position = StableCursor.decode(cursorCodec, scope, cursor);
    if (position != null) {
      query.setBeforeTime(position.getSortTime());
      query.setBeforeId(position.getId());
    }
  }
}

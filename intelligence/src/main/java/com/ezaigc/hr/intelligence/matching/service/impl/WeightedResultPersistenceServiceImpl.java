package com.ezaigc.hr.intelligence.matching.service.impl;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateMatchingStateRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import com.ezaigc.hr.intelligence.matching.mapper.WeightedResultMapper;
import com.ezaigc.hr.intelligence.matching.service.WeightedResultPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** WeightedResult 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class WeightedResultPersistenceServiceImpl
    extends ServiceImpl<WeightedResultMapper, WeightedResult>
    implements WeightedResultPersistenceService {

  @Override
  public List<String> candidateIdsForJdVersion(String jdVersionId, String afterId, int limit) {
    return baseMapper.candidateIdsForJdVersion(jdVersionId, afterId, Math.clamp(limit, 1, 200));
  }

  @Override
  public List<CandidateMatchingStateRow> findCandidateMatchingStates(
      List<String> candidateIds, String ownerId) {
    return candidateIds.isEmpty()
        ? List.of()
        : baseMapper.findCandidateMatchingStates(candidateIds, ownerId);
  }

  @Override
  public List<WeightedResult> findResultsUnsorted(String runId) {
    return baseMapper.selectList(
        Wrappers.lambdaQuery(WeightedResult.class).eq(WeightedResult::getMatchRunId, runId));
  }

  /** 查询结果列表变更后。 */
  @Override
  public List<WeightedResult> findResultsAfter(String runId, int afterRank, int limit) {
    var query =
        Wrappers.lambdaQuery(WeightedResult.class)
            .eq(WeightedResult::getMatchRunId, runId)
            .gt(WeightedResult::getStableRank, afterRank)
            .orderByAsc(WeightedResult::getStableRank, WeightedResult::getCandidateId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  @Override
  public int updateRanks(String runId, List<WeightedResult> rankedResults, Instant now) {
    if (rankedResults == null || rankedResults.isEmpty()) {
      return 0;
    }
    if (rankedResults.size() > MAX_MATCH_RUN_RESUMES) {
      throw new IllegalArgumentException("rank update batch size must not exceed 1000");
    }
    return baseMapper.updateRanks(
        runId, rankedResults, PlatformActorConstants.SYSTEM_ACTOR_ID, now);
  }
}

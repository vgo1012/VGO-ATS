package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.CandidateMatchingStateRow;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightedResult;
import java.time.Instant;
import java.util.List;

/** WeightedResult 实体的持久化服务。 */
public interface WeightedResultPersistenceService extends IService<WeightedResult> {

  List<WeightedResult> findResultsUnsorted(String runId);

  List<String> candidateIdsForJdVersion(String jdVersionId, String afterId, int limit);

  /** ownerId 为空时只允许传入调用方已实时核验的共享候选人 ID。 */
  List<CandidateMatchingStateRow> findCandidateMatchingStates(
      List<String> candidateIds, String ownerId);

  /** 查询结果列表变更后。 */
  List<WeightedResult> findResultsAfter(String runId, int afterRank, int limit);

  /** 在一条参数化 SQL 中原子更新同一运行的全部稳定名次。 */
  int updateRanks(String runId, List<WeightedResult> rankedResults, Instant now);
}

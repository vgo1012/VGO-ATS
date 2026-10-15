package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.CandidateMergeHistory;
import com.ezaigc.hr.talent.mapper.CandidateMergeHistoryMapper;
import com.ezaigc.hr.talent.service.CandidateMergeHistoryPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** CandidateMergeHistory 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CandidateMergeHistoryPersistenceServiceImpl
    extends ServiceImpl<CandidateMergeHistoryMapper, CandidateMergeHistory>
    implements CandidateMergeHistoryPersistenceService {

  /** 新增合并历史。 */
  @Override
  public int insertMergeHistory(
      String id,
      String survivorId,
      String mergedId,
      String conflictId,
      long survivorVersion,
      long mergedVersion,
      String actorId,
      String reason,
      Instant now) {
    CandidateMergeHistory history = new CandidateMergeHistory();
    history.setId(id);
    history.setSurvivorCandidateId(survivorId);
    history.setMergedCandidateId(mergedId);
    history.setIdentityConflictId(conflictId);
    history.setSurvivorVersion(survivorVersion);
    history.setMergedVersion(mergedVersion);
    history.setOperatedByUserId(actorId);
    history.setReason(reason);
    history.setCreateTime(now);
    return baseMapper.insert(history);
  }

  /** 查询合并保留。 */
  @Override
  public String findMergeSurvivor(String mergedId) {
    CandidateMergeHistory history =
        baseMapper.selectOne(
            Wrappers.lambdaQuery(CandidateMergeHistory.class)
                .select(CandidateMergeHistory::getSurvivorCandidateId)
                .eq(CandidateMergeHistory::getMergedCandidateId, mergedId));
    return history == null ? null : history.getSurvivorCandidateId();
  }
}

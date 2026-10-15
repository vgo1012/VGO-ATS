package com.ezaigc.hr.intelligence.matching.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.matching.constant.MatchingConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRunItemOutcome;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunItemOutcomeMapper;
import com.ezaigc.hr.intelligence.matching.service.MatchRunItemOutcomePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** MatchRunItemOutcome 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MatchRunItemOutcomePersistenceServiceImpl
    extends ServiceImpl<MatchRunItemOutcomeMapper, MatchRunItemOutcome>
    implements MatchRunItemOutcomePersistenceService {

  /** 统计成功结果列表。 */
  @Override
  public int countSuccessfulOutcomes(String runId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(MatchRunItemOutcome.class)
                .eq(MatchRunItemOutcome::getMatchRunId, runId)
                .eq(
                    MatchRunItemOutcome::getStatus,
                    MatchRunItemOutcomeStatus.SUCCEEDED.getCode())));
  }

  @Override
  public List<String> findSuccessfulResumeVersionIds(String runId) {
    return baseMapper
        .selectList(
            Wrappers.lambdaQuery(MatchRunItemOutcome.class)
                .select(MatchRunItemOutcome::getResumeVersionId)
                .eq(MatchRunItemOutcome::getMatchRunId, runId)
                .eq(
                    MatchRunItemOutcome::getStatus,
                    MatchRunItemOutcomeStatus.SUCCEEDED.getCode()))
        .stream()
        .map(MatchRunItemOutcome::getResumeVersionId)
        .toList();
  }

  /** 锁定当前已提交条目，超出正式匹配上限时拒绝截断统计。 */
  @Override
  public List<MatchRunItemOutcomeStatus> lockOutcomeStatuses(String runId) {
    List<String> statuses = baseMapper.lockOutcomeStatuses(runId, MatchingConstants.MAX_MATCH_RUN_RESUMES + 1);
    if (statuses.size() > MatchingConstants.MAX_MATCH_RUN_RESUMES) {
      throw new IllegalStateException("matching outcome count exceeds the run limit");
    }
    return statuses.stream().map(MatchRunItemOutcomeStatus::fromCode).toList();
  }
}

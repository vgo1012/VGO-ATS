package com.ezaigc.hr.intelligence.matching.application.adapter;

import com.ezaigc.hr.intelligence.matching.enums.MatchRunStatus;
import com.ezaigc.hr.intelligence.matching.service.MatchRunPersistenceService;
import com.ezaigc.hr.talent.api.ScoreRunCompletionQuery;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只返回已完整成功的评分运行；排队、处理中、部分成功和失败都不能用于简历转入。 */
@Service
public class ScoreRunCompletionAdapter implements ScoreRunCompletionQuery {
  @Resource private MatchRunPersistenceService runs;

  @Override
  @Transactional(readOnly = true)
  public Set<String> successfulRunIds(Collection<String> runIds) {
    if (runIds == null || runIds.isEmpty()) return Set.of();
    if (runIds.size() > MAX_RUNS_PER_BATCH) throw new IllegalArgumentException("评分运行最多1000项");
    return runs.listByIds(runIds).stream()
        .filter(run -> MatchRunStatus.SUCCEEDED.matches(run.getStatus()))
        .map(run -> run.getId())
        .collect(Collectors.toUnmodifiableSet());
  }
}

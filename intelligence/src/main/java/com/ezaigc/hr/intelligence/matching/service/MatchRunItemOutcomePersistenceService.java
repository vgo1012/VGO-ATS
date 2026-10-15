package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchRunItemOutcome;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunItemOutcomeStatus;
import java.util.List;

/** MatchRunItemOutcome 实体的持久化服务。 */
public interface MatchRunItemOutcomePersistenceService extends IService<MatchRunItemOutcome> {

  /** 统计成功结果列表。 */
  int countSuccessfulOutcomes(String runId);

  /** 在终态事务中有界锁定并读取最新已提交的条目状态。 */
  List<MatchRunItemOutcomeStatus> lockOutcomeStatuses(String runId);

  List<String> findSuccessfulResumeVersionIds(String runId);
}

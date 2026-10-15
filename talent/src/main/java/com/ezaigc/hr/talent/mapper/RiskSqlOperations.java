package com.ezaigc.hr.talent.mapper;

import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.RestrictionSummary;
import java.time.Instant;
import java.util.List;

/**
 * 风险显式 SQL 操作契约，承载联表、锁定和条件更新。
 *
 * Mapper-owned cross-module projections, joins, and atomic projection writes.
 */
public interface RiskSqlOperations {
  CompanyBlacklistRecord currentBlacklist(String candidateId);

  /** 统计生效应聘流程列表。 */
  int countActiveApplications(String candidateId);

  /** 统计生效客户推荐列表。 */
  int countActiveSubmissions(String candidateId);

  List<AffectedWorkflow> affected(String candidateId, int limit);

  List<AffectedWorkflow> affectedPage(String candidateId, Instant beforeTime, String beforeId, int limit);

  List<RestrictionSummary> restrictions(String candidateId, Instant now);
}

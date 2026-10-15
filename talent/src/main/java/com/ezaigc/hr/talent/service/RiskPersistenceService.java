package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.RestrictionSummary;
import java.time.Instant;
import java.util.List;

/**
 * 风险持久化服务契约，定义业务数据的读写边界。
 *
 * <p>Persistence boundary used by talent risk managers and query contracts.
 */
public interface RiskPersistenceService extends IService<CompanyBlacklistRecord> {

  CompanyBlacklistRecord currentBlacklist(String candidateId);

  /** 查询黑名单。 */
  CompanyBlacklistRecord findBlacklist(String id);

  /** 新增黑名单。 */
  int insertBlacklist(
      String id,
      String candidateId,
      String type,
      String status,
      String reasonCode,
      String detail,
      String source,
      String evidence,
      String correctsId,
      String actorId,
      Instant occurredAt,
      Instant now);

  /** 统计生效应聘流程列表。 */
  int countActiveApplications(String candidateId);

  /** 统计生效客户推荐列表。 */
  int countActiveSubmissions(String candidateId);

  List<AffectedWorkflow> affected(String candidateId, int limit);

  List<AffectedWorkflow> affectedPage(String candidateId, Instant beforeTime, String beforeId, int limit);

  List<CompanyBlacklistRecord> blacklistHistory(String candidateId, Instant beforeTime, String beforeId, int limit);

  List<RestrictionSummary> restrictions(String candidateId, Instant now);

  List<String> blacklistTags(List<String> candidateIds, Instant now);
}

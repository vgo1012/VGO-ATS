package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.talent.domain.entity.AffectedWorkflow;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistRecord;
import com.ezaigc.hr.talent.domain.entity.RestrictionSummary;
import com.ezaigc.hr.talent.mapper.RiskMapper;
import com.ezaigc.hr.talent.service.RiskPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 风险持久化服务实现，负责把业务操作落实到数据库。
 *
 * <p>Thin persistence implementation; permissions and transactions stay in application managers.
 */
@Service
public class RiskPersistenceServiceImpl extends ServiceImpl<RiskMapper, CompanyBlacklistRecord>
    implements RiskPersistenceService {
  /** 新增黑名单。 */
  @Override
  public int insertBlacklist(
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
      Instant now) {
    CompanyBlacklistRecord record = new CompanyBlacklistRecord();
    record.setId(id);
    record.setCandidateId(candidateId);
    record.setRecordType(type);
    record.setStatus(status);
    record.setReasonCode(reasonCode);
    record.setReasonDetail(detail);
    record.setSourceChannel(source);
    record.setEvidenceReference(evidence);
    record.setCorrectsRecordId(correctsId);
    record.setRecordedByUserId(actorId);
    record.setOccurredAt(occurredAt);
    record.setCreateTime(now);
    record.setVersion(0L);
    return baseMapper.insert(record);
  }

  /** 查询黑名单。 */
  @Override
  public CompanyBlacklistRecord findBlacklist(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public CompanyBlacklistRecord currentBlacklist(String candidateId) {
    return baseMapper.currentBlacklist(candidateId);
  }

  @Override
  public int countActiveApplications(String candidateId) {
    return baseMapper.countActiveApplications(candidateId);
  }

  @Override
  public int countActiveSubmissions(String candidateId) {
    return baseMapper.countActiveSubmissions(candidateId);
  }

  @Override
  public List<AffectedWorkflow> affected(String candidateId, int limit) {
    return baseMapper.affected(candidateId, limit);
  }

  @Override
  public List<AffectedWorkflow> affectedPage(String candidateId, Instant beforeTime, String beforeId, int limit) {
    return baseMapper.affectedPage(candidateId, beforeTime, beforeId, limit);
  }

  @Override
  public List<CompanyBlacklistRecord> blacklistHistory(String candidateId, Instant beforeTime, String beforeId, int limit) {
    var query = Wrappers.lambdaQuery(CompanyBlacklistRecord.class)
        .eq(CompanyBlacklistRecord::getCandidateId, candidateId);
    if (beforeTime != null) {
      query.and(q -> q.lt(CompanyBlacklistRecord::getCreateTime, beforeTime)
          .or(equal -> equal.eq(CompanyBlacklistRecord::getCreateTime, beforeTime)
              .lt(CompanyBlacklistRecord::getId, beforeId)));
    }
    query.orderByDesc(CompanyBlacklistRecord::getCreateTime, CompanyBlacklistRecord::getId);
    return baseMapper.selectPage(new Page<CompanyBlacklistRecord>(1, limit, false), query).getRecords();
  }

  @Override
  public List<RestrictionSummary> restrictions(String candidateId, Instant now) {
    return baseMapper.restrictions(candidateId, now);
  }

  @Override
  public List<String> blacklistTags(List<String> candidateIds, Instant now) {
    return baseMapper.blacklistTags(candidateIds, now);
  }
}

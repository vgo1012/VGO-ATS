package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestriction;
import com.ezaigc.hr.recruiting.submission.mapper.ClientRestrictionMapper;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** ClientRestriction 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ClientRestrictionPersistenceServiceImpl
    extends ServiceImpl<ClientRestrictionMapper, ClientRestriction>
    implements ClientRestrictionPersistenceService {

  @Override
  public Set<String> applicationsWithBlacklistRecords(Set<String> applicationIds) {
    if (applicationIds.isEmpty()) return Set.of();
    return list(Wrappers.lambdaQuery(ClientRestriction.class)
        .select(ClientRestriction::getSourceApplicationId)
        .in(ClientRestriction::getSourceApplicationId, applicationIds))
        .stream().map(ClientRestriction::getSourceApplicationId).collect(Collectors.toSet());
  }

  /** 新增限制。 */
  @Override
  public int insertRestriction(
      String id,
      String clientId,
      String candidateId,
      String submissionId,
      String type,
      String status,
      String intent,
      Instant expiresAt,
      String detail,
      String source,
      Instant occurredAt,
      String correctsId,
      String actorId,
      Instant now) {
    ClientRestriction restriction = new ClientRestriction();
    restriction.setId(id);
    restriction.setClientId(clientId);
    restriction.setCandidateId(candidateId);
    restriction.setSourceSubmissionId(submissionId);
    restriction.setRecordType(type);
    restriction.setStatus(status);
    restriction.setRestrictionIntent(intent);
    restriction.setExpiresAt(expiresAt);
    restriction.setReasonDetail(detail);
    restriction.setSourceChannel(source);
    restriction.setSourceOccurredAt(occurredAt);
    restriction.setCorrectsRecordId(correctsId);
    restriction.setRecordedByUserId(actorId);
    restriction.setCreateTime(now);
    restriction.setVersion(0L);
    return baseMapper.insert(restriction);
  }

  @Override
  public ClientRestriction restriction(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public ClientRestriction revision(String correctsRecordId, String actorId) {
    return baseMapper.selectOne(Wrappers.lambdaQuery(ClientRestriction.class)
        .eq(ClientRestriction::getCorrectsRecordId, correctsRecordId)
        .eq(ClientRestriction::getRecordedByUserId, actorId));
  }

  @Override
  public List<ClientRestriction> restrictions(String candidateId, String clientId, int limit) {
    var query =
        Wrappers.lambdaQuery(ClientRestriction.class)
            .eq(ClientRestriction::getCandidateId, candidateId)
            .eq(clientId != null, ClientRestriction::getClientId, clientId)
            .orderByDesc(ClientRestriction::getCreateTime, ClientRestriction::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }
}

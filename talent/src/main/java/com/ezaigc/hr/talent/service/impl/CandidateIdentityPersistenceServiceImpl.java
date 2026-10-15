package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.mapper.CandidateIdentityMapper;
import com.ezaigc.hr.talent.service.CandidateIdentityPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Identity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CandidateIdentityPersistenceServiceImpl
    extends ServiceImpl<CandidateIdentityMapper, Identity>
    implements CandidateIdentityPersistenceService {

  /** 查询身份。 */
  @Override
  public Identity findIdentity(String type, String hash) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(Identity.class)
            .eq(Identity::getIdentityType, type)
            .eq(Identity::getNormalizedValueHash, hash));
  }

  /** 新增身份。 */
  @Override
  public int insertIdentity(
      String id, String candidateId, String type, String hash, String lastFour, Instant now) {
    Identity identity = new Identity();
    identity.setId(id);
    identity.setCandidateId(candidateId);
    identity.setIdentityType(type);
    identity.setNormalizedValueHash(hash);
    identity.setValueLastFour(lastFour);
    identity.setVerified(true);
    identity.setCreateTime(now);
    identity.setUpdateTime(now);
    return baseMapper.insert(identity);
  }

  /** 删除身份列表。 */
  @Override
  public int deleteIdentities(String candidateId) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(Identity.class).eq(Identity::getCandidateId, candidateId));
  }

  /** 仅移除被替换的主联系方式，保留其他已绑定身份。 */
  @Override
  public int deleteIdentity(String candidateId, String type, String hash) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(Identity.class)
            .eq(Identity::getCandidateId, candidateId)
            .eq(Identity::getIdentityType, type)
            .eq(Identity::getNormalizedValueHash, hash));
  }

  @Override
  public int moveIdentities(String survivorId, String mergedId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(Identity.class)
            .eq(Identity::getCandidateId, mergedId)
            .set(Identity::getCandidateId, survivorId)
            .set(Identity::getUpdateId, actorId)
            .set(Identity::getUpdateTime, now));
  }
}

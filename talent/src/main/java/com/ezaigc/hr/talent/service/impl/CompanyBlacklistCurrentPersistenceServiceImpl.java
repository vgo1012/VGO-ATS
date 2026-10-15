package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.CompanyBlacklistCurrent;
import com.ezaigc.hr.talent.mapper.CompanyBlacklistCurrentMapper;
import com.ezaigc.hr.talent.service.CompanyBlacklistCurrentPersistenceService;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** CompanyBlacklistCurrent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class CompanyBlacklistCurrentPersistenceServiceImpl
    extends ServiceImpl<CompanyBlacklistCurrentMapper, CompanyBlacklistCurrent>
    implements CompanyBlacklistCurrentPersistenceService {

  @Override
  public CompanyBlacklistCurrent lockCurrent(String candidateId) {
    return baseMapper.lockCurrent(candidateId);
  }

  @Override
  public int isBlacklisted(String candidateId) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(CompanyBlacklistCurrent.class)
                .eq(CompanyBlacklistCurrent::getCandidateId, candidateId)
                .eq(CompanyBlacklistCurrent::isActive, true)));
  }

  @Override
  public int projectBlacklist(String candidateId, String expectedRecordId, String recordId,
      boolean active, String actorId, Instant now) {
    if (expectedRecordId == null) {
      CompanyBlacklistCurrent value = new CompanyBlacklistCurrent();
      value.setCandidateId(candidateId);
      value.setRecordId(recordId);
      value.setActive(active);
      value.setCreateId(actorId);
      value.setCreateTime(now);
      value.setUpdateId(actorId);
      value.setUpdateTime(now);
      try {
        return baseMapper.insert(value);
      } catch (DuplicateKeyException concurrentInsert) {
        return 0;
      }
    }
    return baseMapper.update(Wrappers.lambdaUpdate(CompanyBlacklistCurrent.class)
        .eq(CompanyBlacklistCurrent::getCandidateId, candidateId)
        .eq(CompanyBlacklistCurrent::getRecordId, expectedRecordId)
        .set(CompanyBlacklistCurrent::getRecordId, recordId)
        .set(CompanyBlacklistCurrent::isActive, active)
        .set(CompanyBlacklistCurrent::getUpdateId, actorId)
        .set(CompanyBlacklistCurrent::getUpdateTime, now));
  }
}

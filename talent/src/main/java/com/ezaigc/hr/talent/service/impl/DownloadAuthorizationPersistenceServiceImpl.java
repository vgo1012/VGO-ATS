package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.DownloadAuthorization;
import com.ezaigc.hr.talent.mapper.DownloadAuthorizationMapper;
import com.ezaigc.hr.talent.service.DownloadAuthorizationPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** DownloadAuthorization 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class DownloadAuthorizationPersistenceServiceImpl
    extends ServiceImpl<DownloadAuthorizationMapper, DownloadAuthorization>
    implements DownloadAuthorizationPersistenceService {

  /** 新增下载授权。 */
  @Override
  public int insertDownloadAuthorization(
      String id,
      String tokenHash,
      String resumeVersionId,
      String actorId,
      Instant expiresAt,
      Instant now) {
    DownloadAuthorization authorization = new DownloadAuthorization();
    authorization.setId(id);
    authorization.setTokenHash(tokenHash);
    authorization.setResumeVersionId(resumeVersionId);
    authorization.setAuthorizedUserId(actorId);
    authorization.setExpiresAt(expiresAt);
    authorization.setCreateTime(now);
    return baseMapper.insert(authorization);
  }

  /** 查询下载授权。 */
  @Override
  public DownloadAuthorization findDownloadAuthorization(String tokenHash) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(DownloadAuthorization.class)
            .eq(DownloadAuthorization::getTokenHash, tokenHash));
  }

  @Override
  public int consumeDownloadAuthorization(String id, Instant now) {
    DownloadAuthorization authorization = new DownloadAuthorization();
    authorization.setConsumedAt(now);
    return baseMapper.update(
        authorization,
        Wrappers.lambdaUpdate(DownloadAuthorization.class)
            .eq(DownloadAuthorization::getId, id)
            .isNull(DownloadAuthorization::getConsumedAt)
            .gt(DownloadAuthorization::getExpiresAt, now));
  }
}

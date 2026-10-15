package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.DownloadAuthorization;
import java.time.Instant;

/** DownloadAuthorization 实体的持久化服务。 */
public interface DownloadAuthorizationPersistenceService extends IService<DownloadAuthorization> {

  /** 新增下载授权。 */
  int insertDownloadAuthorization(
      String id,
      String tokenHash,
      String resumeVersionId,
      String actorId,
      Instant expiresAt,
      Instant now);

  /** 查询下载授权。 */
  DownloadAuthorization findDownloadAuthorization(String tokenHash);

  int consumeDownloadAuthorization(String id, Instant now);
}

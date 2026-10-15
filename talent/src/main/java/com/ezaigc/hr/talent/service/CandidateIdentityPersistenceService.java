package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.Identity;
import java.time.Instant;

/** Identity 实体的持久化服务。 */
public interface CandidateIdentityPersistenceService extends IService<Identity> {

  /** 查询身份。 */
  Identity findIdentity(String type, String hash);

  /** 新增身份。 */
  int insertIdentity(
      String id, String candidateId, String type, String hash, String lastFour, Instant now);

  /** 删除身份列表。 */
  int deleteIdentities(String candidateId);

  /** 仅删除该候选人的指定身份标识。 */
  int deleteIdentity(String candidateId, String type, String hash);

  int moveIdentities(String survivorId, String mergedId, String actorId, Instant now);
}

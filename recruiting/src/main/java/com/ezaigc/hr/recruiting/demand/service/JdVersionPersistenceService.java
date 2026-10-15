package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import java.time.Instant;
import java.util.List;

/** JdVersionEntity 实体的持久化服务。 */
public interface JdVersionPersistenceService extends IService<JdVersionEntity> {

  JdVersionEntity findReusableVersion(String ownerUserId, String fingerprint);

  JdVersionEntity findActiveExactVersion(
      String ownerUserId,
      String ownerDepartmentId,
      String recruitmentType,
      String title,
      String rawText,
      String clientId,
      String distinction);

  JdVersionEntity findActiveExactVersionForUpdate(
      String ownerUserId,
      String ownerDepartmentId,
      String recruitmentType,
      String title,
      String rawText,
      String clientId,
      String distinction);

  int insertJdVersion(
      String id,
      String jdId,
      int versionNo,
      String title,
      String rawText,
      String sourceType,
      String workRegionCode,
      String actorId,
      Instant now);

  JdVersionEntity findJdVersion(String id);

  List<String> findVersionIds(String jdId);

  List<JdVersionEntity> findJdVersions(String jdId, Instant cursorTime, String cursorId, int limit);
}

package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import java.time.Instant;

/** ResumeVersion 实体的持久化服务。 */
public interface ResumeVersionPersistenceService extends IService<ResumeVersion> {

  String lockForRouting(String id);

  /** 统计简历内容。 */
  int countResumeContent(String resumeId, String sha256);

  /** 查询逻辑简历中已有的相同内容版本。 */
  ResumeVersion findResumeContent(String resumeId, String sha256);

  /** 新增简历版本。 */
  int insertResumeVersion(
      String id,
      String resumeId,
      String intakeId,
      int versionNo,
      String fileName,
      String mediaType,
      long size,
      String sha256,
      String objectKey,
      String actorId,
      Instant now);

  /** 查询简历版本。 */
  ResumeVersion findResumeVersion(String id);
}

package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.Resume;
import java.time.Instant;

/** Resume 实体的持久化服务。 */
public interface ResumePersistenceService extends IService<Resume> {

  /** 查询简历用于候选人。 */
  Resume findResumeForCandidate(String candidateId);

  /** 查询简历。 */
  Resume findResume(String id);

  /** 新增简历。 */
  int insertResume(String id, String candidateId, Instant now);

  /** 更新简历版本。 */
  int updateResumeVersion(String id, int versionNo);

  int moveResumeToCandidate(String survivorId, String mergedId, String actorId, Instant now);

  /** 删除简历。 */
  int deleteResume(String id);
}

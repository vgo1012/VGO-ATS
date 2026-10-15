package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.Resume;
import com.ezaigc.hr.talent.mapper.ResumeMapper;
import com.ezaigc.hr.talent.service.ResumePersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Resume 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ResumePersistenceServiceImpl extends ServiceImpl<ResumeMapper, Resume>
    implements ResumePersistenceService {

  /** 查询简历用于候选人。 */
  @Override
  public Resume findResumeForCandidate(String candidateId) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(Resume.class).eq(Resume::getCandidateId, candidateId));
  }

  /** 查询简历。 */
  @Override
  public Resume findResume(String id) {
    return baseMapper.selectById(id);
  }

  /** 新增简历。 */
  @Override
  public int insertResume(String id, String candidateId, Instant now) {
    Resume resume = new Resume();
    resume.setId(id);
    resume.setCandidateId(candidateId);
    resume.setCurrentVersionNo(1);
    resume.setCreateTime(now);
    resume.setUpdateTime(now);
    return baseMapper.insert(resume);
  }

  /** 更新简历版本。 */
  @Override
  public int updateResumeVersion(String id, int versionNo) {
    Resume resume = new Resume();
    resume.setId(id);
    resume.setCurrentVersionNo(versionNo);
    return baseMapper.updateById(resume);
  }

  @Override
  public int moveResumeToCandidate(
      String survivorId, String mergedId, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(Resume.class)
            .eq(Resume::getCandidateId, mergedId)
            .set(Resume::getCandidateId, survivorId)
            .set(Resume::getUpdateId, actorId)
            .set(Resume::getUpdateTime, now));
  }

  /** 删除简历。 */
  @Override
  public int deleteResume(String id) {
    return baseMapper.deleteById(id);
  }
}

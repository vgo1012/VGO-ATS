package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.enums.ResumeVersionStatus;
import com.ezaigc.hr.talent.mapper.ResumeVersionMapper;
import com.ezaigc.hr.talent.service.ResumeVersionPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ResumeVersion 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ResumeVersionPersistenceServiceImpl
    extends ServiceImpl<ResumeVersionMapper, ResumeVersion>
    implements ResumeVersionPersistenceService {

  @Override
  public String lockForRouting(String id) {
    return baseMapper.lockForRouting(id);
  }

  /** 统计简历内容。 */
  @Override
  public int countResumeContent(String resumeId, String sha256) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ResumeVersion.class)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getContentSha256, sha256)));
  }

  @Override
  public ResumeVersion findResumeContent(String resumeId, String sha256) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(ResumeVersion.class)
            .eq(ResumeVersion::getResumeId, resumeId)
            .eq(ResumeVersion::getContentSha256, sha256)
            .last("LIMIT 1"));
  }

  /** 新增简历版本。 */
  @Override
  public int insertResumeVersion(
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
      Instant now) {
    ResumeVersion version = new ResumeVersion();
    version.setId(id);
    version.setResumeId(resumeId);
    version.setIntakeId(intakeId);
    version.setVersionNo(versionNo);
    version.setOriginalFileName(fileName);
    version.setMediaType(mediaType);
    version.setSizeBytes(size);
    version.setContentSha256(sha256);
    version.setObjectKey(objectKey);
    version.setStatus(ResumeVersionStatus.AVAILABLE.getCode());
    version.setCreateId(actorId);
    version.setCreateTime(now);
    return baseMapper.insert(version);
  }

  /** 查询简历版本。 */
  @Override
  public ResumeVersion findResumeVersion(String id) {
    return baseMapper.selectById(id);
  }
}

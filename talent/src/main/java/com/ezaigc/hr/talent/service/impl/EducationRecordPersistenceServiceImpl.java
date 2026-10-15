package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.EducationRecord;
import com.ezaigc.hr.talent.mapper.EducationRecordMapper;
import com.ezaigc.hr.talent.service.EducationRecordPersistenceService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

/** EducationRecord 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class EducationRecordPersistenceServiceImpl
    extends ServiceImpl<EducationRecordMapper, EducationRecord>
    implements EducationRecordPersistenceService {

  /** 新增教育经历。 */
  @Override
  public int insertEducation(
      String id,
      String candidateId,
      String educationId,
      String applicationId,
      String type,
      String verification,
      String studyMode,
      String source,
      String evidence,
      String note,
      String correctsId,
      String actorId,
      Instant occurredAt,
      Instant now) {
    EducationRecord record = new EducationRecord();
    record.setId(id);
    record.setCandidateId(candidateId);
    record.setEducationFactId(educationId);
    record.setApplicationId(applicationId);
    record.setRecordType(type);
    record.setVerificationStatus(verification);
    record.setStudyMode(studyMode);
    record.setSourceChannel(source);
    record.setEvidenceReference(evidence);
    record.setNote(note);
    record.setCorrectsRecordId(correctsId);
    record.setRecordedByUserId(actorId);
    record.setOccurredAt(occurredAt);
    record.setCreateTime(now);
    record.setVersion(0L);
    return baseMapper.insert(record);
  }

  /** 查询教育经历。 */
  @Override
  public EducationRecord findEducation(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public List<EducationRecord> education(String candidateId, int limit) {
    return baseMapper
        .selectPage(
            new Page<>(1, limit, false),
            Wrappers.lambdaQuery(EducationRecord.class)
                .eq(EducationRecord::getCandidateId, candidateId)
                .orderByDesc(EducationRecord::getOccurredAt, EducationRecord::getId))
        .getRecords();
  }
}

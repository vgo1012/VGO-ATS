package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.EducationRecord;
import java.time.Instant;
import java.util.List;

/** EducationRecord 实体的持久化服务。 */
public interface EducationRecordPersistenceService extends IService<EducationRecord> {

  /** 新增教育经历。 */
  int insertEducation(
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
      Instant now);

  /** 查询教育经历。 */
  EducationRecord findEducation(String id);

  List<EducationRecord> education(String candidateId, int limit);
}

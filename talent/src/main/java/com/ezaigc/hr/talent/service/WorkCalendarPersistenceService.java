package com.ezaigc.hr.talent.service;

import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import com.baomidou.mybatisplus.spring.service.IService;

/** 日历聚合持久化，年度指针串行化同一年度的维护与结算。 */
public interface WorkCalendarPersistenceService extends IService<WorkCalendarRevisionEntity> {
  WorkCalendarYearEntity lockYear(int year);
  WorkCalendarYearEntity findYear(int year);
  int ensureYear(int year);
  int publishPointer(int year, String revisionId);
  List<WorkCalendarRevisionEntity> revisions(int year);
  List<WorkCalendarDayEntity> days(String revisionId);
  int insertDays(List<WorkCalendarDayEntity> days);
  int updateDays(String revisionId, List<LocalDate> dates, String dayType, String description);
  int clearPreview(String id);
  int setPreview(String id, String previewId, String fingerprint, String publishedId, Instant expiresAt);
}

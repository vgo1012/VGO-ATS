package com.ezaigc.hr.talent.service.impl;

import com.ezaigc.hr.talent.domain.entity.WorkCalendarRevisionEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarYearEntity;
import com.ezaigc.hr.talent.domain.entity.WorkCalendarDayEntity;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.mapper.WorkCalendarMapper;
import com.ezaigc.hr.talent.service.WorkCalendarPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WorkCalendarPersistenceServiceImpl
    extends ServiceImpl<WorkCalendarMapper, WorkCalendarRevisionEntity>
    implements WorkCalendarPersistenceService {
  @Override
  public WorkCalendarYearEntity lockYear(int year) {
    return baseMapper.lockYear(year);
  }

  @Override
  public WorkCalendarYearEntity findYear(int year) {
    return baseMapper.findYear(year);
  }

  @Override
  public int ensureYear(int year) {
    return baseMapper.ensureYear(year);
  }

  @Override
  public int publishPointer(int year, String revisionId) {
    return baseMapper.publishPointer(year, revisionId);
  }

  @Override
  public List<WorkCalendarRevisionEntity> revisions(int year) {
    return baseMapper.revisions(year);
  }

  @Override
  public List<WorkCalendarDayEntity> days(String revisionId) {
    return baseMapper.days(revisionId);
  }

  @Override
  public int insertDays(List<WorkCalendarDayEntity> days) {
    return baseMapper.insertDays(days);
  }

  @Override
  public int updateDays(String revisionId, List<LocalDate> dates, String dayType, String description) {
    return baseMapper.updateDays(revisionId, dates, dayType, description);
  }

  @Override
  public int clearPreview(String id) {
    return baseMapper.clearPreview(id);
  }

  @Override
  public int setPreview(String id, String previewId, String fingerprint, String publishedId, Instant expiresAt) {
    return baseMapper.setPreview(id, previewId, fingerprint, publishedId, expiresAt);
  }

}

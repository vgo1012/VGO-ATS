package com.ezaigc.hr.reporting.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.domain.dto.MetricAggregateDTO;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsRowDTO;
import com.ezaigc.hr.reporting.domain.dto.MetricDayDTO;
import com.ezaigc.hr.reporting.domain.dto.ParseCoverageDTO;
import com.ezaigc.hr.reporting.domain.entity.MetricDefinitionEntity;
import com.ezaigc.hr.reporting.domain.entity.MetricEventEntity;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import com.ezaigc.hr.reporting.mapper.MetricMapper;
import com.ezaigc.hr.reporting.service.MetricService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

/** 指标业务服务实现。 */
@Service
public class MetricServiceImpl extends ServiceImpl<MetricMapper, MetricEventEntity>
    implements MetricService {

  @Override public long analyticsResumeCount(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsResumeCount(query); }
  @Override public long analyticsActiveBlacklistCount(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsActiveBlacklistCount(query); }
  @Override public RecruitmentAnalyticsRowDTO analyticsSummary(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsSummary(query); }
  @Override public RecruitmentAnalyticsRowDTO analyticsDemand(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsDemand(query); }
  @Override public List<RecruitmentAnalyticsRowDTO> analyticsTrend(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsTrend(query); }
  @Override public List<RecruitmentAnalyticsRowDTO> analyticsChannels(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsChannels(query); }
  @Override public List<RecruitmentAnalyticsRowDTO> analyticsPortraits(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsPortraits(query); }
  @Override public List<RecruitmentAnalyticsRowDTO> analyticsDepartments(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsDepartments(query); }
  @Override public List<RecruitmentAnalyticsRowDTO> analyticsHrWorkload(RecruitmentAnalyticsQuery query) { return baseMapper.analyticsHrWorkload(query); }

  @Override
  public void capture(Instant from, Instant to, List<String> eventTypes) {
    if (eventTypes == null) {
      throw new IllegalArgumentException("Metric capture event types are required");
    }
    if (from == null || to == null || !from.isBefore(to)) {
      throw new IllegalArgumentException("Unsupported metric capture window");
    }
    LinkedHashSet<MetricEventType> requested = new LinkedHashSet<>();
    // 先校验完整请求，再开始采集，避免后续非法类型导致前面的指标已写入。
    for (String eventType : eventTypes) {
      if (eventType == null) {
        throw new IllegalArgumentException("Metric capture event type is required");
      }
      requested.add(MetricEventType.valueOf(eventType));
    }
    for (MetricEventType eventType : requested) {
      switch (eventType) {
        case RESUME_INGESTED -> baseMapper.captureResumeIngested(from, to);
        case RESUME_PARSED -> baseMapper.captureResumeParsed(from, to);
        case APPLICATION_CREATED -> baseMapper.captureApplications(from, to);
        case HUMAN_SCREENED -> baseMapper.captureHumanScreened(from, to);
        case SUBMISSION_SENT -> baseMapper.captureSubmissions(from, to);
        case INTERVIEW_PASSED -> baseMapper.captureInterviewPassed(from, to);
        case ONBOARDED -> baseMapper.captureOnboarded(from, to);
        default -> throw new IllegalStateException("Validated metric event type became unsupported");
      }
    }
  }

  @Override
  public List<MetricDefinitionEntity> definitions() {
    return baseMapper.definitions();
  }

  @Override
  public ParseCoverageDTO parseCoverage(
      Instant from, Instant to, String jdId, String jdVersionId, String ownerUserId,
      boolean allScope, String actorId, List<String> managedUserIds, List<String> departmentIds) {
    return baseMapper.parseCoverage(from, to, jdId, jdVersionId, ownerUserId, allScope,
        actorId, managedUserIds, departmentIds);
  }

  @Override
  public List<MetricDayDTO> daily(
      Instant from, Instant to, String jdId, String jdVersionId, String ownerUserId,
      boolean allScope, String actorId, List<String> managedUserIds, List<String> departmentIds) {
    return baseMapper.daily(from, to, jdId, jdVersionId, ownerUserId, allScope, actorId, managedUserIds, departmentIds);
  }

  @Override
  public List<MetricAggregateDTO> aggregate(
      Instant from,
      Instant to,
      String jdId,
      String jdVersionId,
      String ownerUserId,
      boolean allScope,
      String actorId,
      List<String> managedUserIds,
      List<String> departmentIds) {
    return baseMapper.aggregate(
        from, to, jdId, jdVersionId, ownerUserId, allScope, actorId, managedUserIds, departmentIds);
  }
}

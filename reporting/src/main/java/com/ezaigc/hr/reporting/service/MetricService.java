package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.dto.MetricAggregateDTO;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsQuery;
import com.ezaigc.hr.reporting.domain.dto.RecruitmentAnalyticsRowDTO;
import com.ezaigc.hr.reporting.domain.entity.MetricDefinitionEntity;
import com.ezaigc.hr.reporting.domain.entity.MetricEventEntity;
import java.time.Instant;
import java.util.List;
import com.ezaigc.hr.reporting.domain.dto.MetricDayDTO;
import com.ezaigc.hr.reporting.domain.dto.ParseCoverageDTO;

public interface MetricService extends IService<MetricEventEntity> {

  long analyticsResumeCount(RecruitmentAnalyticsQuery query);
  long analyticsActiveBlacklistCount(RecruitmentAnalyticsQuery query);
  RecruitmentAnalyticsRowDTO analyticsSummary(RecruitmentAnalyticsQuery query);
  RecruitmentAnalyticsRowDTO analyticsDemand(RecruitmentAnalyticsQuery query);
  List<RecruitmentAnalyticsRowDTO> analyticsTrend(RecruitmentAnalyticsQuery query);
  List<RecruitmentAnalyticsRowDTO> analyticsChannels(RecruitmentAnalyticsQuery query);
  List<RecruitmentAnalyticsRowDTO> analyticsPortraits(RecruitmentAnalyticsQuery query);
  List<RecruitmentAnalyticsRowDTO> analyticsDepartments(RecruitmentAnalyticsQuery query);
  List<RecruitmentAnalyticsRowDTO> analyticsHrWorkload(RecruitmentAnalyticsQuery query);

  void capture(Instant from, Instant to, List<String> eventTypes);

  ParseCoverageDTO parseCoverage(
      Instant from, Instant to, String jdId, String jdVersionId, String ownerUserId,
      boolean allScope, String actorId, List<String> managedUserIds, List<String> departmentIds);

  List<MetricDayDTO> daily(
      Instant from, Instant to, String jdId, String jdVersionId, String ownerUserId,
      boolean allScope, String actorId, List<String> managedUserIds, List<String> departmentIds);

  List<MetricDefinitionEntity> definitions();

  List<MetricAggregateDTO> aggregate(
      Instant from,
      Instant to,
      String jdId,
      String jdVersionId,
      String ownerUserId,
      boolean allScope,
      String actorId,
      List<String> managedUserIds,
      List<String> departmentIds);
}

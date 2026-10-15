package com.ezaigc.hr.reporting.application.assembler;

import com.ezaigc.hr.reporting.domain.dto.MetricAggregateDTO;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import com.ezaigc.hr.reporting.domain.entity.MetricDefinitionEntity;
import com.ezaigc.hr.reporting.domain.response.AuditEventResponse;
import com.ezaigc.hr.reporting.domain.response.MetricAggregateResponse;
import com.ezaigc.hr.reporting.domain.response.MetricDefinitionResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import com.ezaigc.hr.reporting.domain.dto.MetricDayDTO;
import com.ezaigc.hr.reporting.domain.response.MetricDayResponse;
import com.ezaigc.hr.reporting.domain.dto.ParseCoverageDTO;
import com.ezaigc.hr.reporting.domain.response.ParseCoverageResponse;

/** 报表边界对象转换器，负责 DTO、领域对象与响应对象之间的转换。 */
@Component
public class ReportingAssembler {

  public ParseCoverageResponse toParseCoverageResponse(ParseCoverageDTO value) {
    return value == null ? null : new ParseCoverageResponse(
        value.getResumeVersionCount(), value.getParsedResumeVersionCount());
  }

  public List<MetricDayResponse> toMetricDayResponses(
      List<MetricDayDTO> values) {
    return values.stream().map(value -> new MetricDayResponse(
        value.getDate(), value.getEventType(), value.getEventCount())).toList();
  }

  public List<AuditEventResponse> toAuditResponses(List<AuditEventEntity> values) {
    return values.stream().map(this::toResponse).toList();
  }

  public List<MetricDefinitionResponse> toMetricDefinitionResponses(
      List<MetricDefinitionEntity> values) {
    return values.stream().map(this::toResponse).toList();
  }

  public List<MetricAggregateResponse> toMetricAggregateResponses(List<MetricAggregateDTO> values) {
    return values.stream().map(this::toResponse).toList();
  }

  private AuditEventResponse toResponse(AuditEventEntity value) {
    return new AuditEventResponse(
        value.getId(),
        value.getEventType(),
        value.getActorUserId(),
        value.getTargetType(),
        value.getTargetId(),
        value.getOwnerUserId(),
        value.getDepartmentId(),
        value.getReason(),
        value.getBeforeSnapshot(),
        value.getAfterSnapshot(),
        value.getSourceModule(),
        value.getOccurredAt());
  }

  private MetricDefinitionResponse toResponse(MetricDefinitionEntity value) {
    return new MetricDefinitionResponse(
        value.getCode(),
        value.getName(),
        value.getCategory(),
        value.getVersionNo(),
        value.getDefinitionText(),
        value.getEventType(),
        value.getEffectiveFrom());
  }

  private MetricAggregateResponse toResponse(MetricAggregateDTO value) {
    return new MetricAggregateResponse(
        value.getEventType(),
        value.getEventCount(),
        value.getEventValue(),
        value.getFirstOccurredAt(),
        value.getLastOccurredAt());
  }
}

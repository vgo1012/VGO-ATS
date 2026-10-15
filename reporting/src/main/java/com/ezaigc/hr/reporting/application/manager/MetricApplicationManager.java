package com.ezaigc.hr.reporting.application.manager;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.reporting.application.assembler.ReportingAssembler;
import com.ezaigc.hr.reporting.config.MetricCaptureProperties;
import com.ezaigc.hr.reporting.domain.response.MetricReportResponse;
import com.ezaigc.hr.reporting.enums.MetricProjectionStatus;
import com.ezaigc.hr.reporting.service.MetricCaptureService;
import com.ezaigc.hr.reporting.service.MetricService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 指标应用服务，负责用例编排、权限校验和事务边界。 */
@Component
public class MetricApplicationManager {

    /** 授权。 */
    @Resource
    private AuthorizationService authorization;
    /** 时钟。 */
    @Resource
    private Clock clock;
    /** 服务。 */
    @Resource
    private MetricService service;
    @Resource
    private ReportingAssembler assembler;

    @Resource
    private MetricCaptureService captureService;
    @Resource
    private MetricCaptureProperties captureProperties;

    @Transactional(readOnly = true)
    public MetricReportResponse report(
            String reportType,
            Instant from,
            Instant to,
            String jdId,
            String jdVersionId,
            String ownerUserId) {
        authorization.requirePermission("reporting.read-own");
        var actor = authorization.currentActor();
        Instant end = to == null ? clock.instant().plus(1, ChronoUnit.MILLIS) : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        if (!start.isBefore(end)) {
            throw new PlatformException(
                    HttpStatus.BAD_REQUEST, "REPORT_TIME_RANGE_INVALID", "报表开始时间必须早于结束时间", true);
        }
        if (ownerUserId != null
                && !ownerUserId.isBlank()
                && !actor.hasCompanyWideScope()
                && !ownerUserId.equals(actor.getUserId())
                && !actor.getManagedUserIds().contains(ownerUserId)) {
            throw new PlatformException(
                    HttpStatus.FORBIDDEN, "REPORT_SCOPE_FORBIDDEN", "目标用户不在可查看范围", false);
        }
        List<String> eventTypes = eventTypes(reportType);
        if ("overview".equals(reportType) && Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "REPORT_TIME_RANGE_TOO_LONG", "单次统计请选择一年以内的日期范围", true);
        }
        if (!captureProperties.isEnabled()) {
            throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "METRIC_PROJECTION_DISABLED",
                    "报表后台采集已停用，请联系管理员启用", true);
        }
        var projection = captureService.status();
        if (projection.getDataUpdatedAt() == null) {
            throw new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, "METRIC_PROJECTION_INITIALIZING",
                    "报表正在初始化历史数据，请稍后重试", true);
        }
        var aggregate =
                service.aggregate(
                        start,
                        end,
                        CommonUtils.stripToNull(jdId),
                        CommonUtils.stripToNull(jdVersionId),
                        CommonUtils.stripToNull(ownerUserId),
                        actor.hasCompanyWideScope(),
                        actor.getUserId(),
                        List.copyOf(actor.getManagedUserIds()),
                        List.of());
        var definitions =
                service.definitions().stream()
                        .filter(definition -> eventTypes.contains(definition.getEventType()))
                        .toList();
        var points =
                aggregate.stream().filter(point -> eventTypes.contains(point.getEventType())).toList();
        return new MetricReportResponse(
                reportType,
                start,
                end,
                projection.getDataUpdatedAt(),
                assembler.toMetricDefinitionResponses(definitions),
                assembler.toMetricAggregateResponses(points),
                "overview".equals(reportType) ? assembler.toMetricDayResponses(service.daily(
                    start, end, CommonUtils.stripToNull(jdId), CommonUtils.stripToNull(jdVersionId),
                    CommonUtils.stripToNull(ownerUserId), actor.hasCompanyWideScope(),
                    actor.getUserId(), List.copyOf(actor.getManagedUserIds()), List.of())) : List.of(),
                "ai-effect".equals(reportType) ? assembler.toParseCoverageResponse(service.parseCoverage(
                    start, end, CommonUtils.stripToNull(jdId), CommonUtils.stripToNull(jdVersionId),
                    CommonUtils.stripToNull(ownerUserId), actor.hasCompanyWideScope(),
                    actor.getUserId(), List.copyOf(actor.getManagedUserIds()), List.of())) : null,
                projection.getDataUpdatedAt().isBefore(clock.instant().minus(captureProperties.getStaleAfter()))
                        ? MetricProjectionStatus.LAGGING.getCode() : MetricProjectionStatus.READY.getCode(),
                projection.getReconciliationUpdatedAt());
    }

    private static List<String> eventTypes(String reportType) {
        return switch (reportType) {
            case "funnel", "overview" -> List.of(
                    "RESUME_INGESTED",
                    "APPLICATION_CREATED",
                    "SUBMISSION_SENT",
                    "INTERVIEW_PASSED",
                    "ONBOARDED");
            case "workload" -> List.of("HUMAN_SCREENED", "SUBMISSION_SENT", "INTERVIEW_PASSED", "ONBOARDED");
            case "ai-effect" -> List.of("RESUME_INGESTED", "RESUME_PARSED");
            default -> throw new PlatformException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "报表不存在", false);
        };
    }

}

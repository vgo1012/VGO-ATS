package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricReportResponse {
  private String reportType;
  /** 变更前。 */
  private Instant from;
  /** 变更后。 */
  private Instant to;
  /** 数据更新时间。 */
  private Instant dataUpdatedAt;
  /** 定义列表。 */
  private List<MetricDefinitionResponse> definitions;
  /** 分值。 */
  private List<MetricAggregateResponse> points;
  /** 北京时间自然日事件数量；空白日期由客户端补零。 */
  private List<MetricDayResponse> days;
  /** 仅 AI 效果报表提供；分母为本期入库版本，分子为其中已有有效解析快照的版本。 */
  private ParseCoverageResponse parseCoverage;
  /** READY/LAGGING；已有数据即使滞后也保留可读，不当作实时统计。 */
  private String projectionStatus;
  /** 最近七类来源完整补偿周期的最小起点；尚未完成全量补偿时为空。 */
  private Instant reconciliationUpdatedAt;

}

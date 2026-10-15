package com.ezaigc.hr.reporting.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.Value;

/** 经授权的分析范围；部门只能进一步收窄负责人范围。 */
@Value
public class RecruitmentAnalyticsQuery {
  Instant from;
  Instant to;
  Instant asOf;
  String departmentId;
  String jdId;
  String recruitmentType;
  boolean allScope;
  String actorId;
  List<String> managedUserIds;

  /** TIMESTAMP 范围使用绝对秒值，避免 JDBC/session 时区影响过滤。 */
  public BigDecimal getFromEpoch() { return epoch(from); }
  public BigDecimal getToEpoch() { return epoch(to); }
  public BigDecimal getAsOfEpoch() { return epoch(asOf); }
  /** 业务 DATETIME 由生产 JDBC 按上海墙上时间写入，比较时使用相同口径。 */
  public LocalDateTime getAsOfShanghai() { return LocalDateTime.ofInstant(asOf, ZoneId.of("Asia/Shanghai")); }

  private static BigDecimal epoch(Instant value) {
    return BigDecimal.valueOf(value.getEpochSecond()).add(BigDecimal.valueOf(value.getNano(), 9));
  }
}

package com.ezaigc.hr.reporting.domain.entity;

import java.time.Instant;
import lombok.Data;

/** 当前生效指标定义的持久化投影。 */
@Data
public class MetricDefinitionEntity {
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
  /** 分类。 */
  private String category;
  /** 业务版本号。 */
  private int versionNo;
  /** 定义文本。 */
  private String definitionText;
  /** 事件类型。 */
  private String eventType;
  /** 生效起始时间。 */
  private Instant effectiveFrom;
}

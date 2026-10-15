package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import lombok.Value;

/** 数据库阶段聚合；最早 ID 保持原有统计结果顺序。 */
@Value
public class ApplicationStageCountDTO {
  String stage;
  long count;
  String firstApplicationId;
}

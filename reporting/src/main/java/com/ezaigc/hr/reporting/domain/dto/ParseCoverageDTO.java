package com.ezaigc.hr.reporting.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统计期间入库版本的当前解析覆盖情况，重复解析只计一个版本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseCoverageDTO {
  private long resumeVersionCount;
  private long parsedResumeVersionCount;
}

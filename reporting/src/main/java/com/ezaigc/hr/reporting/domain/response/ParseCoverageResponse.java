package com.ezaigc.hr.reporting.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统计期间入库版本的当前解析覆盖情况，不以解析事件次数充当覆盖数。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseCoverageResponse {
  private long resumeVersionCount;
  private long parsedResumeVersionCount;
}

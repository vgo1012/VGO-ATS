package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ControlledCriterionPlanResponse {
  /** 主键 ID。 */
  private String id;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 业务版本号。 */
  private int versionNo;
  /** 方案允许的最大附加分。 */
  private int planMaxBonusPoints;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}

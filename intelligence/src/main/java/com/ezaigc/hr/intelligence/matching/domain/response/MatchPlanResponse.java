package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchPlanResponse {
  /** 主键 ID。 */
  private String id;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 名称。 */
  private String name;
  /** 当前业务版本号。 */
  private int currentVersionNo;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}

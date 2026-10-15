package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WeightProfileResponse {
  /** 主键 ID。 */
  private String id;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 名称。 */
  private String name;
  /** 当前状态。 */
  private String status;
  /** 当前业务版本号。 */
  private int currentVersionNo;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}

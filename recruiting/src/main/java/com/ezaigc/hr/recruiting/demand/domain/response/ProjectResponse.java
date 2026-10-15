package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class ProjectResponse {
  /** 主键 ID。 */
  private String id;
  /** 客户 ID。 */
  private String clientId;
  /** 业务编码。 */
  private String code;
  /** 名称。 */
  private String name;
  /** 当前状态。 */
  private String status;
  /** 技术乐观锁版本号。 */
  private String version;
  /** 更新时间。 */
  private Instant updateTime;
}

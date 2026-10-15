package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class WorkflowTemplateResponse {
  /** 主键 ID。 */
  private String id;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 业务版本号。 */
  private Integer versionNo;
  /** 招聘类型。 */
  private String recruitmentType;
  /** 当前状态。 */
  private String status;
  /** 技术乐观锁版本号。 */
  private String version;
  private String createId;
  /** 生效操作人用户 ID。 */
  private String activatedByUserId;
  /** 生效时间。 */
  private Instant activatedAt;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}

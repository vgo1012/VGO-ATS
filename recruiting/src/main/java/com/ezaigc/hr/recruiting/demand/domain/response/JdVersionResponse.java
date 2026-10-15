package com.ezaigc.hr.recruiting.demand.domain.response;

import java.time.Instant;
import lombok.Data;

@Data
public class JdVersionResponse {
  /** 主键 ID。 */
  private String id;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 稳定的岗位业务编号。 */
  private String jdCode;
  /** 业务版本号。 */
  private Integer versionNo;
  /** 标题。 */
  private String title;
  /** 原始文本内容。 */
  private String rawText;
  /** 当前状态。 */
  private String status;
  /** 数据来源类型。 */
  private String sourceType;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
}

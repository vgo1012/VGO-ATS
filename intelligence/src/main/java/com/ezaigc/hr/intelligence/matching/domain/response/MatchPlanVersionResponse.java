package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MatchPlanVersionResponse {
  /** 主键 ID。 */
  private String id;
  /** 匹配方案 ID。 */
  private String planId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 业务版本号。 */
  private int versionNo;
  /** 当前状态。 */
  private String status;
  /** 受控条件方案版本 ID。 */
  private String controlledCriterionPlanVersionId;
  /** 是否启用技能维度。 */
  private boolean skillsEnabled;
  /** 是否启用角色维度。 */
  private boolean roleEnabled;
  /** 是否启用年限维度。 */
  private boolean yearsEnabled;
  /** 是否启用职责维度。 */
  private boolean responsibilityEnabled;
  /** 是否启用行业项目维度。 */
  private boolean industryProjectEnabled;
  /** 技术乐观锁版本号。 */
  private String version;
  private String createId;
  /** 配置操作人用户 ID。 */
  private String configuredByUserId;
  /** 生效操作人用户 ID。 */
  private String activatedByUserId;
  /** 生效时间。 */
  private Instant activatedAt;
  /** 创建时间。 */
  private Instant createTime;
  /** 更新时间。 */
  private Instant updateTime;
}

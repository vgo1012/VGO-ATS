package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WeightProfileVersionResponse {
  /** 主键 ID。 */
  private String id;
  /** 权重方案 ID。 */
  private String profileId;
  /** 业务版本号。 */
  private int versionNo;
  /** 技能维度权重。 */
  private int skillsWeight;
  /** 角色维度权重。 */
  private int roleWeight;
  /** 年限维度权重。 */
  private int yearsWeight;
  /** 职责维度权重。 */
  private int responsibilityWeight;
  /** 行业项目维度权重。 */
  private int industryProjectWeight;
  private String createId;
  /** 创建时间。 */
  private Instant createTime;
  /** 个人岗位附加条件及默认排序的冻结配置。 */
  private String screeningSettingsJson;
}

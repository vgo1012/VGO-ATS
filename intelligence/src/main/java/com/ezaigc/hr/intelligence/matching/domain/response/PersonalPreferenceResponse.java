package com.ezaigc.hr.intelligence.matching.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PersonalPreferenceResponse {
  /** 主键 ID。 */
  private String id;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 匹配方案版本 ID。 */
  private String planVersionId;
  /** 权重方案版本 ID。 */
  private String weightProfileVersionId;
  /** 业务版本号。 */
  private int versionNo;
  /** 已选条件定义 ID 列表（JSON）。 */
  private String selectedDefinitionIdsJson;
  /** 创建时间。 */
  private Instant createTime;
}

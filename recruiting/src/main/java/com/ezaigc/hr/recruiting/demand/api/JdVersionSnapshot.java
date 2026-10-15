package com.ezaigc.hr.recruiting.demand.api;

import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JdVersionSnapshot {
  private String clientId;
  private String clientName;
  private boolean publicVisible;
  private boolean deleted;
  private Integer headcount;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 职位描述（JD）ID。 */
  private String jdId;
  /** 招聘需求 ID。 */
  private String demandId;
  /** 招聘类型。 */
  private String recruitmentType;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 业务归属部门 ID。 */
  private String ownerDepartmentId;
  private Set<String> collaboratorUserIds;

  public JdVersionSnapshot(
      String jdVersionId,
      String jdId,
      String demandId,
      String recruitmentType,
      String ownerUserId,
      String ownerDepartmentId,
      Set<String> collaboratorUserIds) {
    collaboratorUserIds = collaboratorUserIds == null ? Set.of() : Set.copyOf(collaboratorUserIds);
    this.jdVersionId = jdVersionId;
    this.jdId = jdId;
    this.demandId = demandId;
    this.recruitmentType = recruitmentType;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.collaboratorUserIds = collaboratorUserIds;
  }

}

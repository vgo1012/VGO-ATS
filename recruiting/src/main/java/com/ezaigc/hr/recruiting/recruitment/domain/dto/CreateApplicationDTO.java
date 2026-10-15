package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateApplicationDTO {
  /** 候选人 ID。 */
  private String candidateId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 简历版本 ID。 */
  private String resumeVersionId;
  /** 业务负责人用户 ID。 */
  private String ownerUserId;
  /** 操作原因。 */
  private String reason;
}

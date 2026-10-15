package com.ezaigc.hr.talent.domain.dto;

import lombok.Data;

/** 首次联合解析评分所需的最小收件上下文。 */
@Data
public class IntakeScoringPreparationDTO {
  private String id;
  private String jdVersionId;
  private String handledByUserId;
  private String sourceRecruiterUserId;
  private String createId;
}

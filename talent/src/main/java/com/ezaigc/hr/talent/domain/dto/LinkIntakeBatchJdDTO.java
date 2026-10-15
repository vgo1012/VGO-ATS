package com.ezaigc.hr.talent.domain.dto;

import lombok.Value;

/** 人工核对历史收件后，为整个批次补充唯一岗位版本。 */
@Value
public class LinkIntakeBatchJdDTO {
  String jdVersionId;
  String reason;
}

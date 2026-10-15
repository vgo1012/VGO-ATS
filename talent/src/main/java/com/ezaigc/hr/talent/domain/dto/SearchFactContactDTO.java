package com.ezaigc.hr.talent.domain.dto;

import lombok.Data;
import lombok.ToString;

/** 搜索命中回查结果；联系方式仅从 MySQL 返回，不写入索引。 */
@Data
public class SearchFactContactDTO {
  private String id;
  private String resumeVersionId;
  @ToString.Exclude private String email;
  @ToString.Exclude private String phone;
}

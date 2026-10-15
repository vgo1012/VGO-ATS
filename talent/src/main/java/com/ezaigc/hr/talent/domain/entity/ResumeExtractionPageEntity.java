package com.ezaigc.hr.talent.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeExtractionPageEntity {
  /** 页码。 */
  private int pageNo;
  /** 抽取后的文本内容。 */
  @ToString.Exclude private String extractedText;
}

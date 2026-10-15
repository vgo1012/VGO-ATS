package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SavePersonalPreferenceDTO {
  /** 权重方案版本 ID。 */
  private String weightProfileVersionId;
  private List<String> selectedDefinitionIds;
}

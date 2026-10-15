package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 随个人方案版本冻结的岗位条件与默认排序。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalScreeningSettingsDTO {
  private List<ControlledCriterionDraft> criteria;
  private String defaultSortMode;
}

package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateMatchPlanDTO {
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 名称。 */
  private String name;
  /** 是否启用技能维度。 */
  private boolean skillsEnabled;
  /** 是否启用角色维度。 */
  private boolean roleEnabled;
  /** 是否启用年限维度。 */
  private boolean yearsEnabled;
  /** 是否启用职责维度。 */
  private boolean responsibilityEnabled;
  /** 是否启用行业项目维度。 */
  private boolean industryProjectEnabled;
  /** 条件。 */
  private List<ControlledCriterionDraft> criteria;
}

package com.ezaigc.hr.intelligence.matching.domain.dto;

import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanDetails;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActiveScoringConfigurationDTO {
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 流程模板生效。 */
  private boolean workflowTemplateActive;
  /** 评分配置生效。 */
  private boolean scoringConfigurationActive;
  /** 就绪用于应聘流程。 */
  private boolean readyForApplication;
  /** 评分配置。 */
  private MatchPlanDetails scoringConfiguration;
}

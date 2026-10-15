package com.ezaigc.hr.recruiting.demand.api;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
public class ActiveWorkflowSnapshot {
  /** 模板版本 ID。 */
  private String templateVersionId;
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  /** 招聘类型。 */
  private String recruitmentType;
  /** 步骤列表。 */
  private List<Step> steps;

  public ActiveWorkflowSnapshot(
      String templateVersionId, String jdVersionId, String recruitmentType, List<Step> steps) {
    steps = List.copyOf(steps);
    this.templateVersionId = templateVersionId;
    this.jdVersionId = jdVersionId;
    this.recruitmentType = recruitmentType;
    this.steps = steps;
  }

  @Value
  @Accessors(fluent = true)
  public static class Step {
    String stepDefinitionId;
    String stepCode;
    int sequenceNo;
    boolean candidateOptional;
    String defaultFollowUpChannel;
  }
}

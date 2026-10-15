package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import com.ezaigc.hr.intelligence.matching.enums.MatchRunPurpose;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建匹配运行时在控制器与应用层之间传递的命令。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMatchRunDTO {
  /** 职位描述（JD）版本 ID。 */
  private String jdVersionId;
  private List<String> resumeVersionIds;
  /** 权重方案版本 ID。 */
  private String weightProfileVersionId;
  /** 个人偏好版本 ID。 */
  private String personalPreferenceVersionId;
  private List<String> selectedCriterionIds;
  /** 结果排序模式。 */
  private String sortMode;
  /** 服务端确定的运行用途，HTTP 创建默认为自定义筛选。 */
  private MatchRunPurpose purpose;

  public CreateMatchRunDTO(
      String jdVersionId,
      List<String> resumeVersionIds,
      String weightProfileVersionId,
      String personalPreferenceVersionId,
      List<String> selectedCriterionIds,
      String sortMode) {
    this(jdVersionId, resumeVersionIds, weightProfileVersionId, personalPreferenceVersionId,
        selectedCriterionIds, sortMode, MatchRunPurpose.CUSTOM);
  }
}

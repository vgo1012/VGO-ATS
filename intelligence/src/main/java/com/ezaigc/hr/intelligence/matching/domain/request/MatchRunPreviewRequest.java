package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRunPreviewRequest {
  /** 职位描述（JD）版本 ID。 */
  private @PlatformIdentifier String jdVersionId;
  /** 简历版本 ID。 */
  private @PlatformIdentifier String resumeVersionId;
  /** 权重方案版本 ID。 */
  private @OptionalPlatformIdentifier String weightProfileVersionId;
  private @Size(max = MAX_SELECTED_CRITERIA) List<@PlatformIdentifier String>
      selectedCriterionIds;

}

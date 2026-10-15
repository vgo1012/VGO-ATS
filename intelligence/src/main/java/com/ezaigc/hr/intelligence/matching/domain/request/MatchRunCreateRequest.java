package com.ezaigc.hr.intelligence.matching.domain.request;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_MATCH_RUN_RESUMES;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SELECTED_CRITERIA;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_SORT_MODE_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalPlatformIdentifier;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchRunCreateRequest {
  /** 职位描述（JD）版本 ID。 */
  private @PlatformIdentifier String jdVersionId;
  private @NotEmpty @Size(max = MAX_MATCH_RUN_RESUMES) List<@PlatformIdentifier String>
      resumeVersionIds;
  /** 权重方案版本 ID。 */
  private @OptionalPlatformIdentifier String weightProfileVersionId;
  /** 个人偏好版本 ID。 */
  private @OptionalPlatformIdentifier String personalPreferenceVersionId;
  private @Size(max = MAX_SELECTED_CRITERIA) List<@PlatformIdentifier String>
      selectedCriterionIds;
  /** 结果排序模式。 */
  private @Size(max = MAX_SORT_MODE_LENGTH) @Pattern(regexp = INPUT_PATTERN) String sortMode;

}

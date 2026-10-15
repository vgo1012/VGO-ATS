package com.ezaigc.hr.intelligence.matching.domain.dto;

import com.ezaigc.hr.common.web.error.FieldViolation;
import com.ezaigc.hr.common.web.error.PlatformException;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import java.util.Arrays;

@Data
@NoArgsConstructor
public class ProfessionalWeights {
  /** 技能。 */
  private int skills;
  /** 角色经历。 */
  private int roleExperience;
  /** 相关年限。 */
  private int relatedYears;
  /** 职责匹配。 */
  private int responsibilityMatch;
  /** 行业与项目列表。 */
  private int industryAndProjects;

  public ProfessionalWeights(
      int skills,
      int roleExperience,
      int relatedYears,
      int responsibilityMatch,
      int industryAndProjects) {
    int[] values = {skills, roleExperience, relatedYears, responsibilityMatch, industryAndProjects};
    for (int value : values) {
      if (value < 0 || value > 100) {
        throw invalid("weights", "五维权重必须在 0～100 之间");
      }
    }
    if (Arrays.stream(values).sum() != 100) {
      throw invalid("weights", "五个专业维度权重之和必须为 100");
    }
    this.skills = skills;
    this.roleExperience = roleExperience;
    this.relatedYears = relatedYears;
    this.responsibilityMatch = responsibilityMatch;
    this.industryAndProjects = industryAndProjects;
  }

  /** 返回一份独立的系统默认权重，避免可变 DTO 被跨请求共享和污染。 */
  public static ProfessionalWeights systemDefault() {
    return new ProfessionalWeights(20, 20, 20, 20, 20);
  }

  /** 在保存前重新校验可变 DTO，避免 setter 绕过构造时约束。 */
  public ProfessionalWeights validatedCopy() {
    return new ProfessionalWeights(
        skills, roleExperience, relatedYears, responsibilityMatch, industryAndProjects);
  }

  private static PlatformException invalid(String field, String message) {
    return new PlatformException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "WEIGHT_PROFILE_INVALID",
        message,
        false,
        List.of(new FieldViolation(field, message)));
  }
}

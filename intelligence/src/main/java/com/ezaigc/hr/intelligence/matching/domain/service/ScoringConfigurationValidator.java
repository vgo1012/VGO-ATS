package com.ezaigc.hr.intelligence.matching.domain.service;

import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.common.web.error.FieldViolation;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.dto.ValidatedScoringConfigurationDTO;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.CriterionOperator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 评分配置业务校验器，负责验证输入和领域约束。 */
@Component
public class ScoringConfigurationValidator {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  public ValidatedScoringConfigurationDTO validate(
      boolean skillsEnabled,
      boolean roleEnabled,
      boolean yearsEnabled,
      boolean responsibilityEnabled,
      boolean industryProjectEnabled,
      List<ControlledCriterionDraft> criteria) {
    if (!(skillsEnabled
        && roleEnabled
        && yearsEnabled
        && responsibilityEnabled
        && industryProjectEnabled)) {
      throw invalid("professionalDimensions", "五个专业维度必须全部参与评分，JD 未明确要求也不能关闭维度");
    }
    List<ControlledCriterionDraft> safeCriteria =
        criteria == null ? List.of() : new ArrayList<>(criteria);
    Set<ControlledCriterionCode> facts = new HashSet<>();
    int bonusTotal = 0;
    for (int index = 0; index < safeCriteria.size(); index++) {
      ControlledCriterionDraft criterion = safeCriteria.get(index);
      if (criterion == null || criterion.getCode() == null || criterion.getLevel() == null) {
        throw invalid("criteria[" + index + "]", "受控条件代码和层级不能为空");
      }
      if (!facts.add(criterion.getCode())) {
        throw invalid("criteria[" + index + "].code", "同一事实不能重复配置或重复计分");
      }
      try {
        CriterionOperator.fromCode(criterion.getOperator());
      } catch (IllegalArgumentException exception) {
        throw invalid("criteria[" + index + "].operator", "受控条件操作符不在允许目录内");
      }
      if (CommonUtils.isBlank(criterion.getExpectedValueJson())
          || CommonUtils.isBlank(criterion.getReason())
          || CommonUtils.isBlank(criterion.getEvidencePolicy())) {
        throw invalid("criteria[" + index + "]", "规则、原因和证据策略必须完整");
      }
      try {
        if (!JSON.readTree(criterion.getExpectedValueJson()).isObject()) {
          throw invalid("criteria[" + index + "].expectedValueJson", "受控条件期望值必须是 JSON 对象");
        }
      } catch (JacksonException exception) {
        throw invalid("criteria[" + index + "].expectedValueJson", "受控条件期望值不是合法 JSON");
      }
      if (criterion.getLevel() == ControlledCriterionLevel.PREFER) {
        if (!Set.of(5, 10, 20).contains(criterion.getBonusPoints())) {
          throw invalid("criteria[" + index + "].bonusPoints", "PREFER 附加分只允许 5、10 或 20");
        }
        bonusTotal += criterion.getBonusPoints();
      } else if (criterion.getBonusPoints() != 0) {
        throw invalid("criteria[" + index + "].bonusPoints", "MUST 和 DISPLAY_ONLY 不得配置附加分");
      }
      if (criterion.getLevel() == ControlledCriterionLevel.MUST && criterion.isSelectableByHr()) {
        throw invalid("criteria[" + index + "].selectableByHr", "官方 MUST 不能由个人关闭");
      }
    }
    if (bonusTotal > 30) {
      throw invalid("criteria", "正式配置全部 PREFER 附加分合计不得超过 30");
    }
    return new ValidatedScoringConfigurationDTO(
        skillsEnabled,
        roleEnabled,
        yearsEnabled,
        responsibilityEnabled,
        industryProjectEnabled,
        bonusTotal,
        safeCriteria);
  }

  private static PlatformException invalid(String field, String message) {
    return new PlatformException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "MATCH_PLAN_INVALID",
        message,
        false,
        List.of(new FieldViolation(field, message)));
  }
}

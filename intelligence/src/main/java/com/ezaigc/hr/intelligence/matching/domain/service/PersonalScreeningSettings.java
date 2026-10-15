package com.ezaigc.hr.intelligence.matching.domain.service;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.core.util.Sha256Utils;
import com.ezaigc.hr.intelligence.matching.domain.dto.ControlledCriterionDraft;
import com.ezaigc.hr.intelligence.matching.domain.dto.PersonalScreeningSettingsDTO;
import com.ezaigc.hr.intelligence.matching.domain.entity.ControlledCriterion;
import com.ezaigc.hr.intelligence.matching.domain.entity.WeightProfileVersion;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import com.ezaigc.hr.intelligence.matching.enums.MatchResultSortMode;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** 个人方案随版本冻结；必须项只影响核对通道，不自动决定招聘结果。 */
public final class PersonalScreeningSettings {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private PersonalScreeningSettings() {}

  public static String validateAndSerialize(List<ControlledCriterionDraft> criteria, String sortMode,
      ScoringConfigurationValidator validator) {
    List<ControlledCriterionDraft> safe = criteria == null ? List.of() : criteria;
    MatchResultSortMode sort;
    try {
      sort = sortMode == null ? MatchResultSortMode.REFERENCE_SCORE : MatchResultSortMode.fromCode(sortMode);
    } catch (IllegalArgumentException invalid) {
      throw PlatformExceptions.unprocessable("PERSONAL_SORT_INVALID", "请选择有效排序方式");
    }
    for (var criterion : safe) {
      if (criterion != null && (criterion.getCode() == ControlledCriterionCode.AGE
          || criterion.getCode() == ControlledCriterionCode.GENDER)) {
        throw PlatformExceptions.unprocessable("PERSONAL_CRITERION_INVALID", "个人筛选仅支持岗位相关的学历、证书和语言要求");
      }
    }
    safe = validator.validate(true, true, true, true, true, safe).getCriteria();
    return JSON.writeValueAsString(new PersonalScreeningSettingsDTO(safe, sort.getCode()));
  }

  public static String defaultSort(WeightProfileVersion weights) {
    return weights.getScreeningSettingsJson() == null ? MatchResultSortMode.REFERENCE_SCORE.getCode()
        : JSON.readValue(weights.getScreeningSettingsJson(), PersonalScreeningSettingsDTO.class).getDefaultSortMode();
  }

  public static List<ControlledCriterion> merge(List<ControlledCriterion> official, WeightProfileVersion weights) {
    if (weights.getScreeningSettingsJson() == null) return official;
    PersonalScreeningSettingsDTO settings = JSON.readValue(weights.getScreeningSettingsJson(), PersonalScreeningSettingsDTO.class);
    List<ControlledCriterion> merged = new ArrayList<>(official);
    for (var draft : settings.getCriteria()) {
      // 同一事实不能重复加分；个人必须项仍独立核对，不覆盖公司必须项。
      if (draft.getLevel() == ControlledCriterionLevel.PREFER && official.stream().anyMatch(item ->
          item.getCriterionCode().equals(draft.getCode().getCode())
              && ControlledCriterionLevel.fromCode(item.getLevel()) != ControlledCriterionLevel.DISPLAY_ONLY)) continue;
      var item = new ControlledCriterion();
      item.setId(Sha256Utils.hashUtf8(weights.getId() + ":" + draft.getCode().getCode()).substring(0, 26));
      item.setPlanVersionId(weights.getId());
      item.setCriterionCode(draft.getCode().getCode());
      item.setLevel(draft.getLevel().getCode());
      item.setOperator(draft.getOperator());
      item.setExpectedValueJson(draft.getExpectedValueJson());
      item.setBonusPoints(draft.getBonusPoints());
      item.setReason("个人岗位要求：" + draft.getReason());
      item.setEvidencePolicy(draft.getEvidencePolicy());
      item.setSelectableByHr(false);
      item.setSequenceNo(merged.size() + 1);
      merged.add(item);
    }
    return merged;
  }
}

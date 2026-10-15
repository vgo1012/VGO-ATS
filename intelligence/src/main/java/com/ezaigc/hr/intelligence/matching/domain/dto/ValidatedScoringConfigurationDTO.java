package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ValidatedScoringConfigurationDTO {

    /**
     * 是否启用技能维度。
     */
    private boolean skillsEnabled;
    /**
     * 是否启用角色维度。
     */
    private boolean roleEnabled;
    /**
     * 是否启用年限维度。
     */
    private boolean yearsEnabled;
    /**
     * 是否启用职责维度。
     */
    private boolean responsibilityEnabled;
    /**
     * 是否启用行业项目维度。
     */
    private boolean industryProjectEnabled;
    /**
     * 方案允许的最大附加分。
     */
    private int planMaxBonusPoints;
    /**
     * 条件。
     */
    private List<ControlledCriterionDraft> criteria;

    public ValidatedScoringConfigurationDTO(
            boolean skillsEnabled,
            boolean roleEnabled,
            boolean yearsEnabled,
            boolean responsibilityEnabled,
            boolean industryProjectEnabled,
            int planMaxBonusPoints,
            List<ControlledCriterionDraft> criteria) {
        this.skillsEnabled = skillsEnabled;
        this.roleEnabled = roleEnabled;
        this.yearsEnabled = yearsEnabled;
        this.responsibilityEnabled = responsibilityEnabled;
        this.industryProjectEnabled = industryProjectEnabled;
        this.planMaxBonusPoints = planMaxBonusPoints;
        this.criteria = List.copyOf(criteria);
    }
}

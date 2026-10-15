package com.ezaigc.hr.intelligence.matching.domain.dto;

import static com.ezaigc.hr.common.web.request.RequestCodeFormat.INPUT_PATTERN;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_CRITERION_BONUS_POINTS;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_CRITERION_OPERATOR_LENGTH;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_CRITERION_REASON_LENGTH;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_EVIDENCE_POLICY_LENGTH;
import static com.ezaigc.hr.intelligence.matching.constant.MatchingConstants.MAX_EXPECTED_VALUE_JSON_LENGTH;

import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionCode;
import com.ezaigc.hr.intelligence.matching.enums.ControlledCriterionLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlledCriterionDraft {

    /**
     * 业务编码。
     */
    private @NotNull ControlledCriterionCode code;
    /**
     * 条件满足级别。
     */
    private @NotNull ControlledCriterionLevel level;
    /**
     * 操作符。
     */
    private @NotBlank
            @Size(max = MAX_CRITERION_OPERATOR_LENGTH)
            @Pattern(regexp = INPUT_PATTERN) String operator;
    /**
     * 期望值（JSON）。
     */
    private @NotBlank @Size(max = MAX_EXPECTED_VALUE_JSON_LENGTH) String expectedValueJson;
    /**
     * 附加分值。
     */
    private @Min(0) @Max(MAX_CRITERION_BONUS_POINTS) int bonusPoints;
    /**
     * 操作原因。
     */
    private @NotBlank @Size(max = MAX_CRITERION_REASON_LENGTH) String reason;
    /**
     * 证据使用策略。
     */
    private @NotBlank
            @Size(max = MAX_EVIDENCE_POLICY_LENGTH)
            @Pattern(regexp = INPUT_PATTERN) String evidencePolicy;
    /**
     * 是否允许 HR 选择。
     */
    private boolean selectableByHr;

}

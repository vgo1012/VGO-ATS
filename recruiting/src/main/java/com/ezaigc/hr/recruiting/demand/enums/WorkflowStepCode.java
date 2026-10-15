package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 流程步骤编码 枚举，限定允许使用的业务取值。
 */
@Getter
@RequiredArgsConstructor
public enum WorkflowStepCode implements CodeDescEnum {
    CANDIDATE_COMMUNICATION("候选人沟通"),
    PRE_SUBMISSION_INTERNAL_INTERVIEW("推荐前内部面试"),
    CLIENT_SUBMISSION("客户推荐"),
    CLIENT_AI_INTERVIEW("甲方 AI 面试（人工代录）"),
    INTERVIEW_ROUND_1("第一轮面试"),
    INTERVIEW_ROUND_2("第二轮面试"),
    INTERVIEW_ROUND_3("第三轮面试"),
    DECISION("人工招聘决定"),
    OFFER("Offer 流程");
    /**
     * 枚举值中文说明。
     */
    private final String desc;

    /**
     * 根据 业务编码 创建当前对象。
     */
    public static WorkflowStepCode fromCode(String code) {
        return WorkflowStepCode.valueOf(code);
    }

    public static boolean supports(String code) {
        for (WorkflowStepCode supported : WorkflowStepCode.values()) {
            if (supported.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInterviewStep() {
        return this == PRE_SUBMISSION_INTERNAL_INTERVIEW
                || this == CLIENT_AI_INTERVIEW
                || this == INTERVIEW_ROUND_1
                || this == INTERVIEW_ROUND_2
                || this == INTERVIEW_ROUND_3;
    }

    public boolean isClientStep() {
        return this == CLIENT_SUBMISSION
                || this == CLIENT_AI_INTERVIEW
                || this == INTERVIEW_ROUND_1
                || this == INTERVIEW_ROUND_2
                || this == INTERVIEW_ROUND_3;
    }
}

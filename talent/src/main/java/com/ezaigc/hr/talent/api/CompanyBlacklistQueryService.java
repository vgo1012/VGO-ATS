package com.ezaigc.hr.talent.api;

public interface CompanyBlacklistQueryService {
    boolean isCompanyBlacklisted(String candidateId);

    /** 在调用方写事务内先锁候选人，再锁简历版本、JD 或招聘事实；不阻断历史及终止操作。 */
    void lockCandidateForCommand(String candidateId);

    /** 持有候选人锁并完成幂等回放判断后，以当前风险投影裁决新的招聘推进。 */
    void requireRecruitmentAllowed(String candidateId);
}

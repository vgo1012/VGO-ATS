package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Set;

/** 搜索结果在分页前回查共享库准入；不依赖异步索引更新速度。 */
public interface CandidateLibraryQueryService {
    /** JD 删除完整性守卫：检查全部归属人的当前个人池关联，调用方已授权并锁定逻辑 JD。 */
    boolean hasPersonalPoolResumes(Collection<String> jdVersionIds);

    Set<String> eligibleCandidateIds(Collection<String> candidateIds);

    /** 在共享准入集合中，按全部已保存简历及招聘历史的逻辑 JD 上下文筛选。 */
    Set<String> eligibleCandidateIds(Collection<String> candidateIds, String jdId);

    /** 返回候选集合中属于当前用户个人池的候选人。 */
    Set<String> personalPoolCandidateIds(Collection<String> candidateIds);
    /** 在本人个人池中，按可见岗位上下文匹配逻辑 JD（含历史版本）。 */
    Set<String> personalPoolCandidateIds(Collection<String> candidateIds, String jdId);
    Set<String> personalPoolCandidateIds(Collection<String> candidateIds, String jdId, String poolStage);
}

package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Set;

/** 智能筛选提供仍在待处理队列的精确简历版本，供人才库实时裁决归属。 */
public interface CandidateScreeningStateQuery {
  /** JD 删除完整性检查：不按操作人裁剪，返回这些版本是否仍有未退回的准入。 */
  boolean hasActiveAdmissions(Collection<String> jdVersionIds);

  /** 合并调用已持有候选人锁，按候选人当前读选择、评分与准入历史。 */
  boolean hasMergeProtectedHistory(String candidateId);

  Set<String> activeResumeVersionIds(Collection<String> candidateIds);
}

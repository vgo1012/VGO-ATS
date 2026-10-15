package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.List;
import lombok.Value;

/** 共享候选人返回成功筛选历史；非共享候选人仍只返回当前操作者可见的显式准入。 */
public interface CandidateMatchingStateQuery {
  /** 成功评分关联的候选人事实，按 ID 分页；调用方必须实时核验共享准入后才能放开岗位读取。 */
  default List<String> candidateIdsForJdVersion(String jdVersionId, String afterId, int limit) {
    return List.of();
  }
  List<State> completedStates(Collection<String> candidateIds);

  @Value
  class State {
    String candidateId;
    String resumeVersionId;
    String jdVersionId;
    String matchRunId;
  }
}

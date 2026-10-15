package com.ezaigc.hr.recruiting.recruitment.api;

import java.util.Collection;
import java.util.Set;

/** 招聘模块提供当前用户未完成任务关联的候选人范围；可按任务所属活动的逻辑 JD 过滤。 */
public interface MyTaskCandidateQuery {
  Set<String> candidateIds(Collection<String> candidateIds, String jdId);
}

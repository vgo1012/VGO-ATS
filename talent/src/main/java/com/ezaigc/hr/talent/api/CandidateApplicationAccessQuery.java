package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** 招聘模块提供当前用户作为所属人或协作人可读取的候选人与对应简历版本。 */
public interface CandidateApplicationAccessQuery {
  Map<String, Set<String>> accessibleResumeVersionIds(
      Collection<String> candidateIds, String actorUserId);
}

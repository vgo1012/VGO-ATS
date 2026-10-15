package com.ezaigc.hr.talent.api;

/** 判断某一冻结 JD 版本是否属于当前共享候选人的已保存材料。 */
public interface SharedCandidateJdQuery {
  boolean hasSharedCandidate(String jdVersionId);
}

package com.ezaigc.hr.talent.domain.entity;

import lombok.Data;

/** 候选人在当前操作者可见范围内的待处理身份冲突数。 */
@Data
public class CandidateConflictCountRow {
  private String candidateId;
  private int openIdentityConflictCount;
}

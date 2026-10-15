package com.ezaigc.hr.talent.api;
/** 调用方完成权限校验，并在同一简历路由锁事务内记录有效分流。 */
public interface ResumeLifecycleService {
  void entered(String candidateId, String resumeVersionId);
  void routed(String candidateId, String resumeVersionId, String reason);
  void returned(String candidateId, String resumeVersionId);
}

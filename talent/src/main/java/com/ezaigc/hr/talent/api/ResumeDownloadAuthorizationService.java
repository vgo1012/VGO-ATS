package com.ezaigc.hr.talent.api;

/** 为当前登录账号创建经权限校验、留审计的短期单次简历下载授权。 */
public interface ResumeDownloadAuthorizationService {
  String authorize(String resumeVersionId);
}

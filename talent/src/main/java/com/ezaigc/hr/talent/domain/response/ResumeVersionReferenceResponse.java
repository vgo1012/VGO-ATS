package com.ezaigc.hr.talent.domain.response;

import lombok.Value;

/** 当前可读简历版本的显示名称，不含事实、对象键或文件内容。 */
@Value
public class ResumeVersionReferenceResponse {
  String id;
  int versionNo;
  String originalFileName;
}

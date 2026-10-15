package com.ezaigc.hr.talent.api;

import lombok.Value;
import lombok.experimental.Accessors;

public interface EducationApplicationAccessService {
  /** 校验并获取教育经历访问。 */
  EducationAccess requireEducationAccess(String applicationId, String candidateId);

  @Value
  @Accessors(fluent = true)
  class EducationAccess {
    String applicationId;
    String candidateId;
    String ownerUserId;
    String ownerDepartmentId;
  }
}

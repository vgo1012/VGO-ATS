package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Value;

/** 显式授权分批补充时，排除已经由归属/部门/协作范围计入的申请。 */
@Value
public class ApplicationStageCountQuery {
  Instant from;
  Instant to;
  String jdId;
  String jdVersionId;
  boolean allScope;
  String actorId;
  Set<String> managedUserIds;
  Set<String> departmentIds;
  List<String> grantedApplicationIds;
}

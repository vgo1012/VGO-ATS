package com.ezaigc.hr.intelligence.matching.domain.dto;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

/** 当前岗位已准入简历的授权、姓名和稳定分页条件。 */
@Data
public class ScreeningSelectionQuery {
  private String jdVersionId;
  private String query;
  private String actorId;
  private boolean scopeAll;
  private Set<String> managedUserIds;
  private Set<String> visibleDepartmentIds;
  private Instant beforeTime;
  private String beforeId;
  private int limit;
}

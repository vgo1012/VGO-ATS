package com.ezaigc.hr.recruiting.demand.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 用户可选择的岗位类型和工作区域。 */
@Data
@AllArgsConstructor
public class JdCodeOptionsResponse {
  private List<JdCodeOptionResponse> jobTypes;
  private List<JdCodeOptionResponse> regions;
}

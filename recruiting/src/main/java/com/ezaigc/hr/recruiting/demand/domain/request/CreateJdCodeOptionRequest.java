package com.ezaigc.hr.recruiting.demand.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增编号维度选项，同类型同名称请求返回已有选项。 */
@Data
public class CreateJdCodeOptionRequest {
  @NotBlank @Size(max = 16) @Pattern(regexp = "JOB_TYPE|REGION") private String kind;
  @NotBlank @Size(max = 80) private String name;
}

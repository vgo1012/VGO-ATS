package com.ezaigc.hr.recruiting.demand.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClientRequest {
  /** 兼容旧客户端，输入会被忽略；编码由系统生成。 */
  @Size(max = 64)
  private String code;

  /** 名称。 */
  @NotBlank
  @Size(max = 160)
  private String name;
}

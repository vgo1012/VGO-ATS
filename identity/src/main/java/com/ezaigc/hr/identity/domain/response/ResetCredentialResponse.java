package com.ezaigc.hr.identity.domain.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class ResetCredentialResponse {
  /** 用户 ID。 */
  private String userId;
  /** 重置后的随机一次性口令，仅在本次重置响应中返回。 */
  @ToString.Exclude
  private String oneTimeCredential;
  /** 下次登录是否必须修改密码。 */
  private boolean mustChangePassword;
  /** 技术乐观锁版本号。 */
  private String version;
}

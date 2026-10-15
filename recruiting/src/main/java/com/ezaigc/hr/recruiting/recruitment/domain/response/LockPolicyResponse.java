package com.ezaigc.hr.recruiting.recruitment.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LockPolicyResponse {
  /** 策略键。 */
  private String policyKey;
  /** 时长天数。 */
  private int durationDays;
  /** 技术乐观锁版本号。 */
  private String version;
  private String updateId;
  /** 更新时间。 */
  private Instant updateTime;
}

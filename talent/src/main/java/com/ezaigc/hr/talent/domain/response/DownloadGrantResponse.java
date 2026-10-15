package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class DownloadGrantResponse {
  /** 令牌。 */
  @ToString.Exclude
  private String token;
  /** 过期时间。 */
  private Instant expiresAt;
}

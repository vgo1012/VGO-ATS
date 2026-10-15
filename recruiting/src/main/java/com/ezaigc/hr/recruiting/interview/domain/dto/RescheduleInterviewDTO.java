package com.ezaigc.hr.recruiting.interview.domain.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RescheduleInterviewDTO {
  /** 开始时间。 */
  private Instant startAt;
  /** 结束时间。 */
  private Instant endAt;
  private String timezoneId;
  /** 技术乐观锁版本号。 */
  private long version;
  /** 操作原因。 */
  private String reason;
}

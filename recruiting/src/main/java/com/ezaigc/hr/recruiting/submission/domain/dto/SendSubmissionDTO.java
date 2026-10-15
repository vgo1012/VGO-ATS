package com.ezaigc.hr.recruiting.submission.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendSubmissionDTO {
  /** 接收方地址。 */
  private String recipient;
  /** 通知或沟通渠道。 */
  private String channel;
  /** 操作原因。 */
  private String reason;
  /** 技术乐观锁版本号。 */
  private long version;
}

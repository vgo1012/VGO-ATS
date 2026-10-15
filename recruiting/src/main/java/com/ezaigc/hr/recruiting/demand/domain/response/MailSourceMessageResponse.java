package com.ezaigc.hr.recruiting.demand.domain.response;
import java.time.Instant;
import lombok.Value;
import lombok.ToString;
/** 原始邮件材料详情；正文不作为系统指令执行。 */
@Value
public class MailSourceMessageResponse {
  @ToString.Exclude String id;
  @ToString.Exclude String jdVersionId;
  @ToString.Exclude String sourceMessageId;
  @ToString.Exclude String senderEmail;
  @ToString.Exclude String recipientEmail;
  @ToString.Exclude String subject;
  @ToString.Exclude String plainBody;
  @ToString.Exclude String deliveryType;
  @ToString.Exclude String createId;
  Instant createTime;
}

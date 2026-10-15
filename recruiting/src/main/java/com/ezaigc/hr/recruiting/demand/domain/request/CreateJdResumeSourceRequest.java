package com.ezaigc.hr.recruiting.demand.domain.request;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
/** 模拟邮件来源登记；收件人和传输方式由服务端确定。 */
@Data
public class CreateJdResumeSourceRequest {
  /** 候选人 ID。 */
  @PlatformIdentifier
  @ToString.Exclude
  private String candidateId;
  /** 简历版本 ID。 */
  @PlatformIdentifier
  @ToString.Exclude
  private String resumeVersionId;
  /** 原始 Message-ID。 */
  @NotBlank @Size(max = 998)
  @ToString.Exclude
  private String sourceMessageId;
  /** 发件邮箱。 */
  @NotBlank @Email @Size(max = 254)
  @ToString.Exclude
  private String senderEmail;
  /** 邮件主题。 */
  @NotBlank @Size(max = 500)
  @ToString.Exclude
  private String subject;
  /** 邮件正文。 */
  @NotBlank @Size(max = 220000)
  @ToString.Exclude
  private String plainBody;
}

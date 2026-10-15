package com.ezaigc.hr.talent.domain.dto;

import java.time.Instant;
import lombok.ToString;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResumeProjectionCursorDTO {
  /** 主键 ID。 */
  private String id;
  /** 创建时间。 */
  private Instant createTime;
  /** 来源渠道。 */
  private String sourceChannel;
  /** 所属简历的业务版本号，不能使用事实生成时间替代。 */
  private int resumeVersionNo;
  private long candidateVersion;
  @ToString.Exclude private String fullName;
  @ToString.Exclude private String email;
  @ToString.Exclude private String phone;

  public ResumeProjectionCursorDTO(String id, Instant createTime, String sourceChannel,
      int resumeVersionNo) {
    this(id, createTime, sourceChannel, resumeVersionNo, 0, null, null, null);
  }

  public ResumeProjectionCursorDTO(String id, Instant createTime, String sourceChannel,
      int resumeVersionNo, long candidateVersion, String fullName, String email, String phone) {
    this.id = id;
    this.createTime = createTime;
    this.sourceChannel = sourceChannel;
    this.resumeVersionNo = resumeVersionNo;
    this.candidateVersion = candidateVersion;
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
  }
}

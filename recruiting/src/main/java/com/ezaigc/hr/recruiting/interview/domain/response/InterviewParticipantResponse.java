package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewParticipantResponse {
  /** 面试 ID。 */
  private String interviewId;
  /** 用户 ID。 */
  private String userId;
  /** 面试参与者角色。 */
  private String participantRole;
  /** 响应状态。 */
  private String responseStatus;
  /** 创建时间。 */
  private Instant createTime;
}

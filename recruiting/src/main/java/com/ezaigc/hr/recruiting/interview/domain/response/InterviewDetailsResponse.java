package com.ezaigc.hr.recruiting.interview.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewDetailsResponse {
  /** 面试。 */
  private InterviewResponse interview;
  /** 参与者列表。 */
  private List<InterviewParticipantResponse> participants;
  /** 企业微信日程或会议投影状态；未配置时为空。 */
  private InterviewExternalCalendarResponse externalCalendar;

  public InterviewDetailsResponse(
      InterviewResponse interview, List<InterviewParticipantResponse> participants) {
    this.interview = interview;
    this.participants = participants;
  }
}

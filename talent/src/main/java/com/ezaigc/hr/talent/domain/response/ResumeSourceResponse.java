package com.ezaigc.hr.talent.domain.response;

import java.time.Instant;
import lombok.Value;

/** 该简历版本的实际收件来源，独立于当前收件/渠道列表页。 */
@Value
public class ResumeSourceResponse {
  String intakeId;
  String sourceChannelId;
  String sourceChannelName;
  String channelType;
  String sourceRecruiterUserId;
  Instant receivedAt;
}

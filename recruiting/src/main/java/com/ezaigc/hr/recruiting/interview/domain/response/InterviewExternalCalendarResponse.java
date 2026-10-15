package com.ezaigc.hr.recruiting.interview.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class InterviewExternalCalendarResponse {
    String mode;
    String activeType;
    String status;
    String title;
    String description;
    String adminUserId;
    List<String> inviteeUserIds;
    Integer reminderMinutes;
    Boolean waitingRoomEnabled;
    Boolean allowEnterBeforeHost;
    Boolean enterMuteEnabled;
    String externalObjectId;
    String meetingCode;
    String meetingLink;
    String remoteStatus;
    String lastErrorCode;
    String lastErrorDetail;
    Integer attemptCount;
    Instant lastSyncedAt;
    String version;
    String createIntentId;
}

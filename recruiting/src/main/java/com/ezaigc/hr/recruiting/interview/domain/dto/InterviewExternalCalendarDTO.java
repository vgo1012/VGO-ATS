package com.ezaigc.hr.recruiting.interview.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewExternalCalendarDTO {
    private String mode;
    private String title;
    private String description;
    private String adminUserId;
    private List<String> additionalInviteeUserIds;
    private Integer reminderMinutes;
    private Boolean waitingRoomEnabled;
    private Boolean allowEnterBeforeHost;
    private Boolean enterMuteEnabled;
}

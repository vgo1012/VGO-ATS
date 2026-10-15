package com.ezaigc.hr.recruiting.interview.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 面试在企业微信中的外部投影配置。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewExternalCalendarRequest {
    private @NotBlank @Size(max = 32) String mode;
    private @Size(max = 120) String title;
    private @Size(max = 500) String description;
    private @Size(max = 128) String adminUserId;
    private @Size(max = 100) List<@NotBlank @Size(max = 128) String> additionalInviteeUserIds;
    private @Min(0) @Max(1440) Integer reminderMinutes;
    private Boolean waitingRoomEnabled;
    private Boolean allowEnterBeforeHost;
    private Boolean enterMuteEnabled;
}

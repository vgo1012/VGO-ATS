package com.ezaigc.hr.recruiting.interview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewExternalCalendarReconciliationDTO {
    private long version;
    private String intentId;
    private String action;
    private String externalObjectId;
    private String reason;
    private Boolean originalRequestTerminated;
}

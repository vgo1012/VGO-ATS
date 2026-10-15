package com.ezaigc.hr.talent.domain.dto;

import java.time.Instant;
import lombok.ToString;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 候选人当前搜索状态的单次数据库读取结果，事实正文另按不可变快照读取。 */
@Data
@NoArgsConstructor
public class ResumeSearchStateRow {
    private String candidateId;
    private String candidateStatus;
    private long candidateVersion;
    @ToString.Exclude private String fullName;
    @ToString.Exclude private String email;
    @ToString.Exclude private String phone;
    private String resumeVersionId;
    private int resumeVersionNo;
    private String factSnapshotId;
    private Instant updateTime;
    private String sourceChannel;
}

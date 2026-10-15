package com.ezaigc.hr.talent.domain.dto;

import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 人工纠正简历事实的应用层命令。
 */
@Data
@AllArgsConstructor
public class CorrectResumeFactsDTO {

    /**
     * 简历版本 ID。
     */
    private String resumeVersionId;
    private String baseFactSnapshotId;
    /**
     * 操作原因。
     */
    private String reason;
    /**
     * 事实列表。
     */
    private ResumeFacts facts;
}

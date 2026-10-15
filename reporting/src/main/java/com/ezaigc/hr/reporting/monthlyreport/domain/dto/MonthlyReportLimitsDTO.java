package com.ezaigc.hr.reporting.monthlyreport.domain.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.ezaigc.hr.talent.api.ResumeUploadPolicy;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@NoArgsConstructor(force = true)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportLimitsDTO {
    long maxFileBytes;
    long maxUncompressedBytes;
    int maxRows;
    int maxColumns;
    int maxCellChars;
    int maxGroups;
    int maxZipEntries;
    double maxCompressionRatio;
    int maxManagementRemarksChars;

    @JsonCreator
    public MonthlyReportLimitsDTO(long maxFileBytes, long maxUncompressedBytes, int maxRows, int maxColumns,
                                  int maxCellChars, int maxGroups, int maxZipEntries, double maxCompressionRatio,
                                  int maxManagementRemarksChars) {
        if (maxFileBytes <= 0 || maxUncompressedBytes <= 0 || maxRows <= 0 || maxColumns <= 0
                || maxCellChars <= 0 || maxGroups <= 0 || maxZipEntries <= 0
                || maxCompressionRatio < 1 || maxManagementRemarksChars < 0) {
            throw new IllegalArgumentException("月报解析限制必须为正数");
        }
        this.maxFileBytes = maxFileBytes;
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
        this.maxCellChars = maxCellChars;
        this.maxGroups = maxGroups;
        this.maxZipEntries = maxZipEntries;
        this.maxCompressionRatio = maxCompressionRatio;
        this.maxManagementRemarksChars = maxManagementRemarksChars;
    }

    public static MonthlyReportLimitsDTO defaults() {
        return new MonthlyReportLimitsDTO(ResumeUploadPolicy.MAX_FILE_SIZE_BYTES, 64L * 1024 * 1024, 100_000, 256, 32_767,
                300, 2_000, 100.0, 2_000);
    }
}

package com.ezaigc.hr.reporting.monthlyreport.domain.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Value;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

import java.io.InputStream;

/** 仅用于控制器传输文件，不能作为 JSON 返回。 */
@Value
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor_ = @JsonCreator)
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MonthlyReportDownloadResponse {
    String fileName;
    long sizeBytes;
    InputStream stream;
}

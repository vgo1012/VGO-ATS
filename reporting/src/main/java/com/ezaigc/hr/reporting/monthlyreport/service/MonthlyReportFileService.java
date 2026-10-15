package com.ezaigc.hr.reporting.monthlyreport.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import java.time.Instant;
import java.util.List;

public interface MonthlyReportFileService extends IService<MonthlyReportFileEntity> {
    List<MonthlyReportFileEntity> forReport(String reportId);
    List<MonthlyReportFileEntity> expired(Instant now, int limit);
    List<MonthlyReportFileEntity> expiredAfter(Instant now, Instant afterExpiry, String afterId, int limit);
}

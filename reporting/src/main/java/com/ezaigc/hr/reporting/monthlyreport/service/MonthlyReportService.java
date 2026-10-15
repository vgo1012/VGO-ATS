package com.ezaigc.hr.reporting.monthlyreport.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import java.time.Instant;
import java.util.List;

public interface MonthlyReportService extends IService<MonthlyReportEntity> {
    MonthlyReportEntity lock(String id);
    MonthlyReportEntity findRequest(String actorId, String key);
    long activeCount();
    List<MonthlyReportEntity> staleUploads(Instant before, int limit);
    List<MonthlyReportEntity> recent(String month, Instant before, String beforeId, int limit);
}

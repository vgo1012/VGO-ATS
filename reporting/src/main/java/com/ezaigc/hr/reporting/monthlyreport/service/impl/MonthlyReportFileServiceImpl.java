package com.ezaigc.hr.reporting.monthlyreport.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportFileEntity;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportFileMapper;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportFileService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MonthlyReportFileServiceImpl extends ServiceImpl<MonthlyReportFileMapper, MonthlyReportFileEntity> implements MonthlyReportFileService {
    @Override public List<MonthlyReportFileEntity> forReport(String reportId) {
        return list(Wrappers.<MonthlyReportFileEntity>lambdaQuery().eq(MonthlyReportFileEntity::getReportId, reportId).orderByAsc(MonthlyReportFileEntity::getFileKind));
    }
    @Override public List<MonthlyReportFileEntity> expired(Instant now, int limit) {
        return expiredAfter(now, null, null, limit);
    }
    @Override public List<MonthlyReportFileEntity> expiredAfter(Instant now, Instant afterExpiry, String afterId, int limit) {
        return page(new Page<>(1, Math.min(100, Math.max(1, limit)), false),
                Wrappers.<MonthlyReportFileEntity>lambdaQuery().eq(MonthlyReportFileEntity::isDeleted, false)
                .le(MonthlyReportFileEntity::getExpiresAt, now)
                .and(afterExpiry != null && afterId != null, query -> query.gt(MonthlyReportFileEntity::getExpiresAt, afterExpiry)
                        .or(equal -> equal.eq(MonthlyReportFileEntity::getExpiresAt, afterExpiry).gt(MonthlyReportFileEntity::getId, afterId)))
                .orderByAsc(MonthlyReportFileEntity::getExpiresAt, MonthlyReportFileEntity::getId)).getRecords();
    }
}

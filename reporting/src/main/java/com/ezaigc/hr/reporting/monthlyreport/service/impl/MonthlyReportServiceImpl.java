package com.ezaigc.hr.reporting.monthlyreport.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.reporting.monthlyreport.domain.entity.MonthlyReportEntity;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportStatus;
import com.ezaigc.hr.reporting.monthlyreport.mapper.MonthlyReportMapper;
import com.ezaigc.hr.reporting.monthlyreport.service.MonthlyReportService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MonthlyReportServiceImpl extends ServiceImpl<MonthlyReportMapper, MonthlyReportEntity> implements MonthlyReportService {
    @Override public MonthlyReportEntity lock(String id) {
        return baseMapper.lock(id);
    }
    @Override public MonthlyReportEntity findRequest(String actorId, String key) {
        return getOne(Wrappers.<MonthlyReportEntity>lambdaQuery().eq(MonthlyReportEntity::getOwnerUserId, actorId).eq(MonthlyReportEntity::getIdempotencyKey, key).last("FOR UPDATE"));
    }
    @Override public long activeCount() {
        return baseMapper.lockActiveIds().size();
    }
    @Override public List<MonthlyReportEntity> staleUploads(Instant before, int limit) {
        int bounded = Math.min(100, Math.max(1, limit));
        var query = Wrappers.<MonthlyReportEntity>lambdaQuery()
                .eq(MonthlyReportEntity::getStatus, MonthlyReportStatus.UPLOADING.getCode())
                .lt(MonthlyReportEntity::getUpdateTime, before).orderByAsc(MonthlyReportEntity::getUpdateTime);
        return page(new Page<>(1, bounded, false), query).getRecords();
    }
    @Override public List<MonthlyReportEntity> recent(String month, Instant before, String beforeId, int limit) {
        var query = Wrappers.<MonthlyReportEntity>lambdaQuery()
                .select(MonthlyReportEntity::getId, MonthlyReportEntity::getReportMonth, MonthlyReportEntity::getDataAsOf,
                        MonthlyReportEntity::getStatus, MonthlyReportEntity::getVersion, MonthlyReportEntity::getCreateTime,
                        MonthlyReportEntity::getCreateName, MonthlyReportEntity::getOfferCount, MonthlyReportEntity::getTotalScore,
                        MonthlyReportEntity::getErrorCode, MonthlyReportEntity::getErrorDetail, MonthlyReportEntity::getExpiresAt)
                .eq(month != null, MonthlyReportEntity::getReportMonth, month)
                .ne(MonthlyReportEntity::getStatus, MonthlyReportStatus.DELETED.getCode());
        if (before != null) query.and(q -> q.lt(MonthlyReportEntity::getCreateTime, before)
                .or(n -> n.eq(MonthlyReportEntity::getCreateTime, before).lt(MonthlyReportEntity::getId, beforeId)));
        int bounded = Math.min(101, Math.max(1, limit));
        query.orderByDesc(MonthlyReportEntity::getCreateTime, MonthlyReportEntity::getId);
        return page(new Page<>(1, bounded, false), query).getRecords();
    }
}

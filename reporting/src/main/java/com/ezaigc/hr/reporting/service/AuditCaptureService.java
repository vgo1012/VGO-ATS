package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.entity.AuditCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.AuditCaptureLane;
import com.ezaigc.hr.reporting.enums.AuditCaptureSource;

/** 审计投影增量采集服务契约。 */
public interface AuditCaptureService extends IService<AuditCaptureCheckpointEntity> {

  int MAX_BATCH_SIZE = 1000;

  int captureNext(AuditCaptureSource source, AuditCaptureLane lane, int limit);
}

package com.ezaigc.hr.reporting.service;

import com.ezaigc.hr.reporting.domain.dto.MetricProjectionStatusDTO;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.entity.MetricCaptureCheckpointEntity;
import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;

public interface MetricCaptureService extends IService<MetricCaptureCheckpointEntity> {
  int captureNext(MetricEventType source, MetricCaptureLane lane, int limit);
  MetricProjectionStatusDTO status();
}

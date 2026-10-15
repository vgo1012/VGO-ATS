package com.ezaigc.hr.reporting.application.manager;

import com.ezaigc.hr.reporting.enums.MetricCaptureLane;
import com.ezaigc.hr.reporting.enums.MetricEventType;
import com.ezaigc.hr.reporting.service.MetricCaptureService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 单个来源单个批次拥有短事务，写投影与推进检查点不可分离。 */
@Component
public class MetricCaptureApplicationManager {
  @Resource private MetricCaptureService service;

  // 源事实使用一致性读，避免 INSERT SELECT 与面评等在线事务反序加锁；检查点仍显式锁定。
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int captureNext(MetricEventType source, MetricCaptureLane lane, int limit) {
    return service.captureNext(source, lane, limit);
  }
}

package com.ezaigc.hr.reporting.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.reporting.domain.entity.AuditEventEntity;
import java.time.Instant;
import java.util.List;

public interface AuditService extends IService<AuditEventEntity> {

  void capture();

  List<AuditEventEntity> search(
      Instant from,
      Instant to,
      String eventType,
      String actorUserId,
      String targetType,
      String targetId,
      Instant beforeAt,
      String beforeId,
      boolean allScope,
      String currentUserId,
      List<String> managedUserIds,
      List<String> departmentIds,
      int limit);
}

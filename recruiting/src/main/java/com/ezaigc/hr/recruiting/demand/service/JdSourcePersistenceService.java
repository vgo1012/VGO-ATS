package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.common.web.pagination.StableCursor;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdSourceEntity;
import java.util.List;

public interface JdSourcePersistenceService extends IService<JdSourceEntity> {
  JdSourceEntity findSource(String actorId, String sourceType, String sourceKey);
  List<JdSourceEntity> findPage(String jdVersionId, String actorId, boolean scopeAll, StableCursor cursor, int limit);
}

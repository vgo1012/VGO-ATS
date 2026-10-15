package com.ezaigc.hr.recruiting.demand.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import java.util.List;
import com.ezaigc.hr.common.web.pagination.StableCursor;

public interface JdResumeSourcePersistenceService extends IService<JdResumeSourceEntity> {
  void insertIfAbsent(JdResumeSourceEntity row);
  JdResumeSourceEntity findExisting(String mailSourceId, String contentSha256);
  List<JdResumeSourceEntity> findVisiblePage(String jdVersionId, StableCursor cursor, int fetchSize, String actorId, boolean scopeAll);
  List<JdResumeSourceEntity> findPage(String jdVersionId, StableCursor cursor, int fetchSize);
  long countByJdVersion(String jdVersionId);
}

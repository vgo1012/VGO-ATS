package com.ezaigc.hr.recruiting.recruitment.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.PathChange;
import java.util.List;

/** PathChange 实体的持久化服务。 */
public interface PathChangePersistenceService extends IService<PathChange> {

  List<PathChange> findPathChanges(String applicationId);
}

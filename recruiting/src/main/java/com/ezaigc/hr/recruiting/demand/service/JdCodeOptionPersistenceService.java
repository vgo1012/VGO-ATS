package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import java.util.List;

/** JD 编号维度选项持久化服务。 */
public interface JdCodeOptionPersistenceService extends IService<JdCodeOptionEntity> {
  List<JdCodeOptionEntity> findCodeOptions();
  JdCodeOptionEntity findCodeOption(String kind, String code);
  JdCodeOptionEntity findCodeOptionByName(String kind, String name);
  String lastOptionCode(String kind);
  int insertCodeOption(JdCodeOptionEntity option);

}

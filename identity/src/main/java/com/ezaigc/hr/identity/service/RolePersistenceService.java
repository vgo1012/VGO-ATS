package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.RoleEntity;
import java.util.List;
import java.util.Set;

/** RoleEntity 实体的持久化服务。 */
public interface RolePersistenceService extends IService<RoleEntity> {

  String findActiveRoleId(String code);

  /** 一次查询指定编码对应的启用角色，供账号初始化批量校验使用。 */
  List<RoleEntity> findActiveRolesByCodes(Set<String> codes);
}

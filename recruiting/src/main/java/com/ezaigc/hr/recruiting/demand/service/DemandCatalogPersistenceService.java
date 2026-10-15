package com.ezaigc.hr.recruiting.demand.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import java.time.Instant;

public interface DemandCatalogPersistenceService
    extends IService<ClientEntity>, DemandCatalogSqlOperations {
  /** 查询客户。 */
  ClientEntity findClient(String id);

  /** 仅删除指定版本客户；现有业务外键禁止删除被引用客户。 */
  int deleteClient(String id, long version);

  /** 统计客户编码。 */
  int countClientCode(String code);

  /** 新增客户。 */
  int insertClient(
      String id,
      String code,
      String name,
      String ownerUserId,
      String ownerDepartmentId,
      String actorId,
      Instant now);

  /** 更新客户。 */
  int updateClient(String id, String name, long version, String actorId, Instant now);

  /** 停用客户。 */
  int disableClient(String id, long version, String actorId, Instant now);
}

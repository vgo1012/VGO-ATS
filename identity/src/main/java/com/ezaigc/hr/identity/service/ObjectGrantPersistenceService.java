package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.ObjectGrant;
import java.util.Collection;
import java.util.List;

/** ObjectGrant 实体的持久化服务。 */
public interface ObjectGrantPersistenceService extends IService<ObjectGrant> {

  List<String> findGrantedObjectIdPage(String userId, String objectType, String permissionCode, String afterObjectId);

  int countObjectGrant(String userId, String objectType, String objectId, String permissionCode);

  List<String> findGrantedObjectIds(
      String userId, String objectType, Collection<String> objectIds, String permissionCode);
}

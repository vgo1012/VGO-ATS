package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.ObjectGrant;
import com.ezaigc.hr.identity.mapper.ObjectGrantMapper;
import com.ezaigc.hr.identity.service.ObjectGrantPersistenceService;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/** ObjectGrant 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ObjectGrantPersistenceServiceImpl extends ServiceImpl<ObjectGrantMapper, ObjectGrant>
    implements ObjectGrantPersistenceService {

  @Override
  public List<String> findGrantedObjectIdPage(String userId, String objectType, String permissionCode, String afterObjectId) {
    return baseMapper.findGrantedObjectIdPage(userId, objectType, permissionCode, afterObjectId);
  }

  @Override
  public int countObjectGrant(
      String userId, String objectType, String objectId, String permissionCode) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ObjectGrant.class)
                .eq(ObjectGrant::getUserId, userId)
                .eq(ObjectGrant::getObjectType, objectType)
                .eq(ObjectGrant::getObjectId, objectId)
                .in(ObjectGrant::getPermissionCode, permissionCode, "*")));
  }

  @Override
  public List<String> findGrantedObjectIds(
      String userId, String objectType, Collection<String> objectIds, String permissionCode) {
    if (objectIds == null || objectIds.isEmpty()) {
      return List.of();
    }
    return baseMapper.selectObjs(
        Wrappers.lambdaQuery(ObjectGrant.class)
            .select(ObjectGrant::getObjectId)
            .eq(ObjectGrant::getUserId, userId)
            .eq(ObjectGrant::getObjectType, objectType)
            .in(ObjectGrant::getObjectId, objectIds)
            .in(ObjectGrant::getPermissionCode, permissionCode, "*"));
  }
}

package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ASSIGNABLE_ROLES_PER_USER;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.RoleEntity;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.mapper.RoleMapper;
import com.ezaigc.hr.identity.service.RolePersistenceService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** RoleEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class RolePersistenceServiceImpl extends ServiceImpl<RoleMapper, RoleEntity>
    implements RolePersistenceService {

  @Override
  public String findActiveRoleId(String code) {
    RoleEntity role =
        baseMapper.selectOne(
            Wrappers.lambdaQuery(RoleEntity.class)
                .select(RoleEntity::getId)
                .eq(RoleEntity::getCode, code)
                .eq(RoleEntity::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
    return role == null ? null : role.getId();
  }

  @Override
  public List<RoleEntity> findActiveRolesByCodes(Set<String> codes) {
    if (codes == null
        || codes.size() > MAX_ASSIGNABLE_ROLES_PER_USER
        || codes.stream().anyMatch(code -> code == null || code.isBlank())) {
      throw new IllegalArgumentException("identity role code batch is invalid");
    }
    if (codes.isEmpty()) {
      return List.of();
    }
    return baseMapper.selectList(
        Wrappers.lambdaQuery(RoleEntity.class)
            .select(RoleEntity::getId, RoleEntity::getCode)
            .in(RoleEntity::getCode, codes)
            .eq(RoleEntity::getStatus, IdentityActivationStatus.ACTIVE.getCode()));
  }
}

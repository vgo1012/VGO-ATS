package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SUPPORT_DEPARTMENTS_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.USER_ORGANIZATION_WRITE_BATCH_SIZE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.Membership;
import com.ezaigc.hr.identity.mapper.MembershipMapper;
import com.ezaigc.hr.identity.service.MembershipPersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Membership 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class MembershipPersistenceServiceImpl extends ServiceImpl<MembershipMapper, Membership>
    implements MembershipPersistenceService {

  /** 新增成员关系。 */
  @Override
  public int insertMembership(
      String userId, String departmentId, String membershipType, String actorId, Instant now) {
    return baseMapper.insert(membership(userId, departmentId, membershipType, actorId, now));
  }

  @Override
  public boolean insertOrganizationMemberships(
      String userId,
      String homeDepartmentId,
      Set<String> supportDepartmentIds,
      String actorId,
      Instant now) {
    if (userId == null
        || homeDepartmentId == null
        || supportDepartmentIds == null
        || supportDepartmentIds.size() > MAX_SUPPORT_DEPARTMENTS_PER_USER
        || supportDepartmentIds.stream().anyMatch(Objects::isNull)
        || actorId == null
        || now == null) {
      throw new IllegalArgumentException("identity organization membership batch is invalid");
    }
    List<Membership> memberships = new ArrayList<>(supportDepartmentIds.size() + 1);
    memberships.add(membership(userId, homeDepartmentId, "HOME", actorId, now));
    for (String departmentId : supportDepartmentIds) {
      if (!homeDepartmentId.equals(departmentId)) {
        memberships.add(membership(userId, departmentId, "SUPPORT", actorId, now));
      }
    }
    return saveBatch(memberships, USER_ORGANIZATION_WRITE_BATCH_SIZE);
  }

  /** 删除成员关系操作人类型。 */
  @Override
  public int deleteMembershipByType(String userId, String membershipType) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(Membership.class)
            .eq(Membership::getUserId, userId)
            .eq(Membership::getMembershipType, membershipType));
  }

  private static Membership membership(
      String userId, String departmentId, String membershipType, String actorId, Instant now) {
    Membership membership = new Membership();
    membership.setUserId(userId);
    membership.setDepartmentId(departmentId);
    membership.setMembershipType(membershipType);
    membership.setCreateId(actorId);
    membership.setCreateTime(now);
    return membership;
  }
}

package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.Membership;
import java.time.Instant;
import java.util.Set;

/** Membership 实体的持久化服务。 */
public interface MembershipPersistenceService extends IService<Membership> {

  /** 新增成员关系。 */
  int insertMembership(
      String userId, String departmentId, String membershipType, String actorId, Instant now);

  /** 批量新增一个账号的主部门与支援部门成员关系。 */
  boolean insertOrganizationMemberships(
      String userId,
      String homeDepartmentId,
      Set<String> supportDepartmentIds,
      String actorId,
      Instant now);

  /** 删除成员关系操作人类型。 */
  int deleteMembershipByType(String userId, String membershipType);
}

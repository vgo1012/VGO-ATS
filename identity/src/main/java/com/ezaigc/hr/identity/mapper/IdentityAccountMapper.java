package com.ezaigc.hr.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.identity.domain.dto.DirectoryUserDTO;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.service.IdentityAccountSqlOperations;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdentityAccountMapper
    extends BaseMapper<UserAccountRecord>, IdentityAccountSqlOperations {

  @SpecialSql("登录尝试按规范化邮箱锁定同一账号行，串行化失败计数与凭据校验")
  @Select("SELECT id, display_name, work_email, normalized_email, credential_hash, "
      + "failed_login_count, failed_login_window_started_at, login_locked_until, source_title, "
      + "home_department_id, direct_leader_user_id, status, must_change_password, registered_at, "
      + "credential_reset_at, disabled_at, version, ext, remark, create_id, create_name, "
      + "create_time, update_id, update_name, update_time "
      + "FROM identity_user_account WHERE normalized_email = #{normalizedEmail} FOR UPDATE")
  UserAccountRecord lockLoginAccount(@Param("normalizedEmail") String normalizedEmail);

  @SpecialSql("仅对初始化记录存在且仍启用超级管理员角色的账号豁免可触发的登录锁定")
  @Select("SELECT EXISTS (SELECT 1 FROM identity_user_account u"
      + " JOIN identity_user_account_history h ON h.user_id = u.id AND h.change_type = 'BOOTSTRAPPED'"
      + " JOIN identity_user_role ur ON ur.user_id = u.id"
      + " JOIN identity_role r ON r.id = ur.role_id AND r.code = 'SUPER_ADMIN' AND r.status = '"
      + IdentityActivationStatus.Codes.ACTIVE
      + "' WHERE u.id = #{userId} AND u.status = '"
      + IdentityActivationStatus.Codes.ACTIVE + "')")
  boolean isUsableBootstrappedSuperAdmin(@Param("userId") String userId);

  @SpecialSql("无有效初始化管理员时按账号ID选择一名在用超级管理员，保留紧急登录恢复入口")
  @Select("""
      SELECT EXISTS (
        SELECT 1 FROM identity_user_account u
        JOIN identity_user_role ur ON ur.user_id = u.id
        JOIN identity_role r ON r.id = ur.role_id AND r.code = 'SUPER_ADMIN' AND r.status = #{activeStatus}
        WHERE u.id = #{userId} AND u.status = #{activeStatus}
          AND NOT EXISTS (
            SELECT 1 FROM identity_user_account bootstrap_user
            JOIN identity_user_account_history h ON h.user_id = bootstrap_user.id
              AND h.change_type = 'BOOTSTRAPPED'
            JOIN identity_user_role bootstrap_role ON bootstrap_role.user_id = bootstrap_user.id
            JOIN identity_role bootstrap_role_def ON bootstrap_role_def.id = bootstrap_role.role_id
              AND bootstrap_role_def.code = 'SUPER_ADMIN' AND bootstrap_role_def.status = #{activeStatus}
            WHERE bootstrap_user.status = #{activeStatus}
          )
          AND NOT EXISTS (
            SELECT 1 FROM identity_user_account earlier_user
            JOIN identity_user_role earlier_role ON earlier_role.user_id = earlier_user.id
            JOIN identity_role earlier_role_def ON earlier_role_def.id = earlier_role.role_id
              AND earlier_role_def.code = 'SUPER_ADMIN' AND earlier_role_def.status = #{activeStatus}
            WHERE earlier_user.status = #{activeStatus} AND earlier_user.id < u.id
          )
      )
      """)
  boolean isFallbackEmergencySuperAdmin(
      @Param("userId") String userId, @Param("activeStatus") String activeStatus);

  @SpecialSql("业务选人目录在分页前排除持有有效系统管理员角色的账号，避免联表导致重复成员")
  @Select("""
      <script>
      SELECT u.id, u.display_name, u.home_department_id, u.status
      FROM identity_user_account u
      WHERE u.status = #{activeStatus}
        AND NOT EXISTS (
          SELECT 1 FROM identity_user_role ur
          JOIN identity_role r ON r.id = ur.role_id
          WHERE ur.user_id = u.id AND r.status = #{activeStatus} AND r.code = 'SUPER_ADMIN'
        )
        <if test="query != null">AND u.display_name LIKE CONCAT('%', #{query}, '%')</if>
        <if test="afterId != null">AND u.id &gt; #{afterId}</if>
      ORDER BY u.id
      LIMIT #{limit}
      </script>
      """)
  List<DirectoryUserDTO> findDirectoryUsers(
      @Param("query") String query, @Param("afterId") String afterId, @Param("limit") int limit,
      @Param("activeStatus") String activeStatus);

  @SpecialSql("连接用户角色和有效角色关系查询登录账号角色代码")
  @Select(
      "SELECT r.code FROM identity_user_role ur"
          + " JOIN identity_role r ON r.id = ur.role_id"
          + " WHERE ur.user_id = #{userId} AND r.status = '"
          + IdentityActivationStatus.Codes.ACTIVE
          + "' ORDER BY r.code")
  /** 查询角色编码列表。 */
  List<String> findRoleCodes(@Param("userId") String userId);

  @SpecialSql("连接用户角色、有效角色和角色权限并去重权限代码")
  @Select(
      "SELECT DISTINCT rp.permission_code FROM identity_user_role ur"
          + " JOIN identity_role r ON r.id = ur.role_id AND r.status = '"
          + IdentityActivationStatus.Codes.ACTIVE
          + "' JOIN identity_role_permission rp ON rp.role_id = ur.role_id"
          + " WHERE ur.user_id = #{userId} ORDER BY rp.permission_code")
  /** 查询权限编码列表。 */
  List<String> findPermissionCodes(@Param("userId") String userId);

  @SpecialSql("使用递归 CTE 解析当前用户的完整下属树")
  @Select(
      "WITH RECURSIVE managed_users AS ("
          + " SELECT id FROM identity_user_account WHERE direct_leader_user_id = #{userId} AND status = '"
          + IdentityActivationStatus.Codes.ACTIVE
          + "' UNION ALL SELECT account.id FROM identity_user_account account"
          + " JOIN managed_users managed ON account.direct_leader_user_id = managed.id"
          + " WHERE account.status = '"
          + IdentityActivationStatus.Codes.ACTIVE
          + "') SELECT id FROM managed_users ORDER BY id")
  List<String> findManagedUserIds(@Param("userId") String userId);

  @SpecialSql("历史招聘报表按当前直属领导链读取下属，穿过停用节点；不扩大日常业务授权")
  @Select("""
      WITH RECURSIVE managed_users(id) AS (
        SELECT id FROM identity_user_account WHERE direct_leader_user_id = #{userId}
        UNION DISTINCT SELECT account.id FROM identity_user_account account
        JOIN managed_users managed ON account.direct_leader_user_id = managed.id
      ) SELECT id FROM managed_users WHERE id != #{userId} ORDER BY id
      """)
  List<String> findReportingManagedUserIds(@Param("userId") String userId);
}

package com.ezaigc.hr.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.HandoverBatchRecord;
import com.ezaigc.hr.identity.enums.IdentityActivationStatus;
import com.ezaigc.hr.identity.service.IdentityAdministrationSqlOperations;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdentityAdministrationMapper
    extends BaseMapper<DepartmentRecord>, IdentityAdministrationSqlOperations {

  @SpecialSql("连接部门、分配队列和负责人生成管理视图")
  @Select(
      """
      SELECT d.id, d.code, d.name, d.parent_department_id,
             q.owner_user_id AS assignment_queue_owner_user_id,
             owner.display_name AS assignment_queue_owner_name,
             d.status, d.version, d.create_id, d.create_time, d.update_id, d.update_time,
             d.ext, d.remark, d.create_id, d.create_name, d.create_time,
             d.update_id, d.update_name, d.update_time
        FROM identity_department d
        LEFT JOIN identity_department_assignment_queue q ON q.department_id = d.id
        LEFT JOIN identity_user_account owner ON owner.id = q.owner_user_id
       ORDER BY d.code, d.id
      """)
  /** 查询部门列表。 */
  List<DepartmentRecord> findDepartments();

  @SpecialSql("连接部门、分配队列和负责人读取单个管理视图")
  @Select(
      """
      SELECT d.id, d.code, d.name, d.parent_department_id,
             q.owner_user_id AS assignment_queue_owner_user_id,
             owner.display_name AS assignment_queue_owner_name,
             d.status, d.version, d.create_id, d.create_time, d.update_id, d.update_time,
             d.ext, d.remark, d.create_id, d.create_name, d.create_time,
             d.update_id, d.update_name, d.update_time
        FROM identity_department d
        LEFT JOIN identity_department_assignment_queue q ON q.department_id = d.id
        LEFT JOIN identity_user_account owner ON owner.id = q.owner_user_id
       WHERE d.id = #{id}
      """)
  DepartmentRecord findDepartmentById(@Param("id") String id);

  @SpecialSql("连接账号、部门和直属领导并执行可选条件分页查询")
  @Select(
      """
      SELECT u.id, u.display_name, u.work_email, u.source_title, u.home_department_id,
             d.name AS home_department_name, u.direct_leader_user_id,
             leader.display_name AS direct_leader_name, u.status,
             u.must_change_password, u.version
        FROM identity_user_account u
        JOIN identity_department d ON d.id = u.home_department_id
        LEFT JOIN identity_user_account leader ON leader.id = u.direct_leader_user_id
       WHERE (#{query} IS NULL OR u.display_name LIKE CONCAT('%', #{query}, '%')
              OR CONVERT(u.normalized_email USING utf8mb4) COLLATE utf8mb4_bin LIKE CONCAT('%', #{query}, '%'))
         AND ((#{status} IS NULL AND u.status <> 'DELETED') OR u.status = #{status})
       ORDER BY u.registered_at DESC, u.id DESC
       LIMIT #{limit}
      """)
  /** 查询用户列表。 */
  List<AdminUserRecord> findUsers(
      @Param("query") String query, @Param("status") String status, @Param("limit") int limit);

  @SpecialSql("连接账号、部门和直属领导并按账号主键读取管理视图")
  @Select(
      """
      SELECT u.id, u.display_name, u.work_email, u.source_title, u.home_department_id,
             d.name AS home_department_name, u.direct_leader_user_id,
             leader.display_name AS direct_leader_name, u.status,
             u.must_change_password, u.version
        FROM identity_user_account u
        JOIN identity_department d ON d.id = u.home_department_id
        LEFT JOIN identity_user_account leader ON leader.id = u.direct_leader_user_id
       WHERE u.id = #{id}
      """)
  @Override
  AdminUserRecord findUserById(@Param("id") String id);

  @SpecialSql("连接账号、部门和直属领导并按有限账号ID批量读取管理视图")
  @Select("""
      <script>
      SELECT u.id, u.display_name, u.work_email, u.source_title, u.home_department_id,
             d.name AS home_department_name, u.direct_leader_user_id,
             leader.display_name AS direct_leader_name, u.status,
             u.must_change_password, u.version
        FROM identity_user_account u
        JOIN identity_department d ON d.id = u.home_department_id
        LEFT JOIN identity_user_account leader ON leader.id = u.direct_leader_user_id
       WHERE <choose><when test="ids != null and !ids.isEmpty()">u.id IN
         <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
       </when><otherwise>1 = 0</otherwise></choose>
      </script>
      """)
  @Override
  List<AdminUserRecord> findUsersByIds(@Param("ids") List<String> ids);

  @SpecialSql("连接账号、部门、直属领导和角色筛选管理视图")
  @Select(
      """
      SELECT u.id, u.display_name, u.work_email, u.source_title, u.home_department_id,
             d.name AS home_department_name, u.direct_leader_user_id,
             leader.display_name AS direct_leader_name, u.status,
             u.must_change_password, u.version
        FROM identity_user_account u
        JOIN identity_department d ON d.id = u.home_department_id
        LEFT JOIN identity_user_account leader ON leader.id = u.direct_leader_user_id
        JOIN identity_user_role ur ON ur.user_id = u.id
        JOIN identity_role r ON r.id = ur.role_id
       WHERE r.code = #{roleCode} AND u.status <> 'DELETED'
       ORDER BY u.status, u.display_name, u.id
      """)
  /** 按角色查询用户列表。 */
  List<AdminUserRecord> findUsersByRole(@Param("roleCode") String roleCode);

  @SpecialSql("使用数据库原子 upsert 更新部门分配队列并递增版本")
  @Insert(
      """
      INSERT INTO identity_department_assignment_queue
          (department_id, owner_user_id, version, configured_by_user_id, configured_at,
           create_id, create_time, update_id, update_time)
      VALUES (#{departmentId}, #{ownerUserId}, 0, #{actorId}, #{now},
              #{actorId}, #{now}, #{actorId}, #{now})
      ON DUPLICATE KEY UPDATE
          owner_user_id = VALUES(owner_user_id), version = version + 1,
          configured_by_user_id = VALUES(configured_by_user_id), configured_at = VALUES(configured_at),
          update_id = VALUES(update_id), update_time = VALUES(update_time)
      """)
  int upsertAssignmentQueue(
      @Param("departmentId") String departmentId,
      @Param("ownerUserId") String ownerUserId,
      @Param("actorId") String actorId,
      @Param("now") Instant now);

  @SpecialSql("连接有效超级管理员关系并加行锁保护最后管理员约束")
  @Select(
      "SELECT u.id FROM identity_user_role ur"
          + " JOIN identity_role r ON r.id = ur.role_id AND r.code = 'SUPER_ADMIN'"
          + " JOIN identity_user_account u ON u.id = ur.user_id AND u.status = '"
          + IdentityActivationStatus.Codes.ACTIVE
          + "' ORDER BY u.id FOR UPDATE")
  List<String> findActiveSuperAdminUserIdsForUpdate();

  /** 删除会话列表操作人安全主体名称。 */
  @SpecialSql("按主体名称清理 Spring Session 基础设施表")
  @Delete("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = #{principalName}")
  int deleteSessionsByPrincipalName(@Param("principalName") String principalName);

  @SpecialSql("按完成或开始时间的合并表达式排序最近交接批次")
  @Select(
      """
      SELECT id, source_user_id, target_user_id, selection_mode, status,
             total_count, succeeded_count, failed_count, unresolved_count,
             reason, requested_by_user_id, started_at, completed_at, version, idempotency_key,
             create_id, create_time, update_id, update_time,
             ext, remark, create_id, create_name, create_time, update_id, update_name, update_time
        FROM identity_handover_batch
       WHERE source_user_id = #{sourceUserId}
       ORDER BY COALESCE(completed_at, started_at) DESC, id DESC
       LIMIT 100
      """)
  /** 按来源查询交接列表。 */
  List<HandoverBatchRecord> findHandoversBySource(@Param("sourceUserId") String sourceUserId);

  /** 更新指定业务记录。 */
  @SpecialSql("以初始版本为条件原子汇总交接结果并递增版本")
  @Update(
      """
      UPDATE identity_handover_batch
         SET status = #{status}, succeeded_count = #{succeededCount}, failed_count = #{failedCount},
             unresolved_count = #{unresolvedCount}, completed_at = #{completedAt}, version = version + 1
       WHERE id = #{id} AND version = 0
      """)
  /** 完成交接批次。 */
  int completeHandoverBatch(
      @Param("id") String id,
      @Param("status") String status,
      @Param("succeededCount") int succeededCount,
      @Param("failedCount") int failedCount,
      @Param("unresolvedCount") int unresolvedCount,
      @Param("completedAt") Instant completedAt);
}

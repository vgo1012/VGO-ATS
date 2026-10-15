package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 身份账户持久化服务契约，定义业务数据的读写边界。
 * <p>
 * Persistence boundary for authenticated account loading.
 */
public interface IdentityAccountPersistenceService
        extends IService<UserAccountRecord>, IdentityAccountSqlOperations {

    /**
     * 查询操作人规范化邮箱。
     */
    UserAccountRecord findByNormalizedEmail(String normalizedEmail);

    /** 登录事务内锁定账号，防止并发错误口令绕过计数。 */
    UserAccountRecord lockLoginAccount(String normalizedEmail);

    boolean isUsableBootstrappedSuperAdmin(String userId);

    /** 内置管理员不再可用时，选出一个在用超级管理员作为登录恢复入口。 */
    boolean isFallbackEmergencySuperAdmin(String userId);

    int updateLoginFailure(String userId, int count, Instant windowStartedAt, Instant lockedUntil);

    int clearLoginFailures(String userId);

    /** 角色实际变更时推进账号版本，令尚未保存或未被删除的旧会话失效。 */
    int advanceSessionVersion(String userId, String actorId, Instant now);

    UserAccountRecord findById(String userId);

    /** 最小业务选人目录；排除有效超级管理员，姓名检索和过滤均在数据库分页前完成。 */
    List<UserAccountRecord> findDirectoryUsers(String query, String afterId, int limit);

    /** 精确解析历史引用，包含停用账号，最多由应用层传入 100 个 ID。 */
    List<UserAccountRecord> resolveDirectoryUsers(Set<String> ids);

    String findDirectLeaderId(String userId);

    /**
     * 统计用于唯一匹配的规范化邮箱。
     */
    int countNormalizedEmail(String normalizedEmail);

    int countActiveUsersInDepartment(String departmentId);

    /**
     * 新增用户。
     */
    int insertUser(
            String id,
            String displayName,
            String workEmail,
            String normalizedEmail,
            String credentialHash,
            String sourceTitle,
            String departmentId,
            String leaderId,
            String actorId,
            Instant now);

    /**
     * 更新用户。
     */
    int updateUser(
            String id,
            String displayName,
            String workEmail,
            String normalizedEmail,
            String sourceTitle,
            String departmentId,
            String leaderId,
            long version,
            String actorId,
            Instant now);

    /**
     * 更新凭据。
     */
    int updateCredential(
            String id, String credentialHash, boolean mustChange, String actorId, Instant now);

    /**
     * 停用用户。
     */
    int disableUser(String id, long version, String actorId, Instant now);

    /** 逻辑删除用户，保留账号、角色、组织关系和业务历史。 */
    int deleteUser(String id, long version, String actorId, Instant now);
}

package com.ezaigc.hr.identity.application.adapter;

import static com.ezaigc.hr.common.web.request.RequiredIdentifier.require;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.identity.enums.IdentityRoleCode;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdentityDirectoryAdapter implements IdentityDirectoryService {

    @Resource
    private IdentityAccountPersistenceService accounts;

    @Resource
    private IdentityAdministrationPersistenceService administration;

    @Override
    public UserReference activeUser(String userId) {
        var user = accounts.findById(userId);
        if (user == null || !user.active()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "TARGET_USER_NOT_ACTIVE", "目标用户不存在或已停用", false);
        }
        return new UserReference(user.getId(), user.getDisplayName(), user.getHomeDepartmentId());
    }

    @Override
    public DepartmentReference activeDepartment(String departmentId) {
        String id = require(departmentId, "ownerDepartmentId");
        var department = administration.findDepartmentById(id);
        if (department == null || !department.active()) {
            throw new PlatformException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "OWNER_DEPARTMENT_NOT_ACTIVE",
                    "JD 归属部门不存在或已停用",
                    false);
        }
        return new DepartmentReference(
                department.getId(), department.getCode(), department.getName(),
                department.getParentDepartmentId());
    }

    @Override
    public boolean isRecruiterOwner(String userId) {
        var user = accounts.findById(userId);
        return user != null && user.active() && accounts.findRoleCodes(userId).stream()
                .anyMatch(role -> IdentityRoleCode.RECRUITER.matches(role)
                        || IdentityRoleCode.RECRUITMENT_MANAGER.matches(role)
                        || IdentityRoleCode.HR_DIRECTOR.matches(role)
                        || IdentityRoleCode.SUPER_ADMIN.matches(role));
    }

    @Override
    public Map<String, UserReference> activeUsers(Set<String> userIds) {
        if (userIds == null) {
            throw PlatformExceptions.unprocessable("TARGET_USERS_REQUIRED", "目标用户集合不能为空");
        }
        if (userIds.size() > MAX_ACTIVE_USER_BATCH_SIZE) {
            throw PlatformExceptions.unprocessable(
                    "TARGET_USERS_LIMIT_EXCEEDED", "单次最多校验 100 个目标用户");
        }
        Set<String> normalizedIds = new LinkedHashSet<>();
        for (String userId : userIds) {
            normalizedIds.add(require(userId, "userIds[]"));
        }
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<String, UserAccountRecord> accountsById = new LinkedHashMap<>();
        for (UserAccountRecord account : accounts.listByIds(normalizedIds)) {
            if (account != null && account.getId() != null) {
                accountsById.put(account.getId(), account);
            }
        }
        Map<String, UserReference> result = new LinkedHashMap<>();
        for (String userId : normalizedIds) {
            UserAccountRecord account = accountsById.get(userId);
            if (account == null || !account.active()) {
                throw new PlatformException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "TARGET_USER_NOT_ACTIVE",
                        "目标用户不存在或已停用",
                        false);
            }
            result.put(
                    userId,
                    new UserReference(
                            account.getId(), account.getDisplayName(), account.getHomeDepartmentId()));
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<String, String> userNames(Set<String> userIds) {
        Set<String> ids = directoryNameIds(userIds);
        if (ids.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var user : accounts.listByIds(ids)) {
            result.put(user.getId(), user.getDisplayName());
        }
        return Map.copyOf(result);
    }

    @Override
    public Map<String, String> departmentNames(Set<String> departmentIds) {
        Set<String> ids = directoryNameIds(departmentIds);
        if (ids.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var department : administration.listByIds(ids)) {
            result.put(department.getId(), department.getName());
        }
        return Map.copyOf(result);
    }

    private Set<String> directoryNameIds(Set<String> requestedIds) {
        if (requestedIds == null || requestedIds.size() > MAX_ACTIVE_USER_BATCH_SIZE) {
            throw PlatformExceptions.unprocessable("DIRECTORY_NAMES_LIMIT_INVALID", "目录名称查询须提供最多 100 个 ID");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String id : requestedIds) ids.add(require(id, "ids[]"));
        return ids;
    }
}

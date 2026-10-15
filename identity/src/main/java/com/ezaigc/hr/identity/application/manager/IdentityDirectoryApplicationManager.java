package com.ezaigc.hr.identity.application.manager;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorCodec;
import com.ezaigc.hr.common.web.pagination.CursorPage;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import com.ezaigc.hr.common.web.request.RequiredIdentifier;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.domain.response.DirectoryUserResponse;
import com.ezaigc.hr.identity.domain.response.DirectoryDepartmentResponse;
import com.ezaigc.hr.identity.service.IdentityAccountPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 人员展示目录不授予分配、转交或查看他人流程的权限。 */
@Service
@Transactional(readOnly = true)
public class IdentityDirectoryApplicationManager {
  @Resource private AuthorizationService authorization;
  @Resource private IdentityAccountPersistenceService accounts;
  @Resource private IdentityAdministrationPersistenceService administration;
  @Resource private CursorCodec cursors;

  public CursorPage<DirectoryUserResponse> users(String query, String cursor, Integer requestedLimit) {
    CurrentActorSnapshot actor = requireDirectoryAccess();
    String normalized = query == null || query.isBlank() ? null : query.strip();
    if (normalized != null && normalized.length() > 100) {
      throw PlatformExceptions.badRequest("DIRECTORY_QUERY_INVALID", "姓名搜索词不能超过 100 字");
    }
    int limit = PageLimits.resolve(requestedLimit, 20, 100);
    String scope = CursorScopes.scope("identity.directory", actor.getUserId(),
        normalized != null, normalized == null ? "" : normalized);
    String afterId = null;
    if (cursor != null && !cursor.isBlank()) {
      try {
        afterId = RequiredIdentifier.require(cursors.decode(cursor, scope, 1).getFirst(), "cursorId");
      } catch (IllegalArgumentException exception) {
        throw PlatformExceptions.badRequest("CURSOR_INVALID", "分页游标无效，请重新搜索");
      }
    }
    List<UserAccountRecord> rows = accounts.findDirectoryUsers(normalized, afterId, limit + 1);
    boolean hasMore = rows.size() > limit;
    List<UserAccountRecord> page = rows.subList(0, Math.min(rows.size(), limit));
    String next = hasMore ? cursors.encode(scope, List.of(page.getLast().getId())) : null;
    return new CursorPage<>(page.stream().map(this::response).toList(), next, hasMore);
  }

  public List<DirectoryUserResponse> resolve(Set<String> ids) {
    requireDirectoryAccess();
    if (ids == null || ids.isEmpty() || ids.size() > 100) {
      throw PlatformExceptions.badRequest("DIRECTORY_IDS_INVALID", "每次查询需要 1～100 个成员编号");
    }
    Set<String> validated = ids.stream().map(id -> RequiredIdentifier.require(id, "userId"))
        .collect(Collectors.toSet());
    return accounts.resolveDirectoryUsers(validated).stream().map(this::response).toList();
  }

  public List<DirectoryDepartmentResponse> departments() {
    authorization.requirePermission("demand.manage");
    CurrentActorSnapshot actor = authorization.currentActor();
    return administration.findDepartments().stream()
        .filter(department -> department != null && department.active())
        .filter(department -> actor.hasCompanyWideScope()
            || actor.affiliatedDepartmentIds().contains(department.getId()))
        .map(department -> new DirectoryDepartmentResponse(
            department.getId(), department.getCode(), department.getName(),
            department.getParentDepartmentId(), department.getStatus()))
        .toList();
  }

  private CurrentActorSnapshot requireDirectoryAccess() {
    CurrentActorSnapshot actor = authorization.currentActor();
    if (List.of("talent.read", "demand.read", "recruitment.read", "interview.read").stream()
        .noneMatch(actor::hasPermission)) {
      throw PlatformExceptions.forbidden("DIRECTORY_ACCESS_DENIED", "当前账号没有招聘业务人员目录权限");
    }
    return actor;
  }

  private DirectoryUserResponse response(UserAccountRecord user) {
    return new DirectoryUserResponse(user.getId(), user.getDisplayName(),
        user.getHomeDepartmentId(), user.getStatus());
  }
}

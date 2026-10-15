package com.ezaigc.hr.identity.controller;

import static com.ezaigc.hr.common.web.version.ResourceVersion.parse;
import static com.ezaigc.hr.identity.constant.IdentityConstants.ASSIGNABLE_ROLE_CODE_INPUT_PATTERN;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_ROLE_CODE_INPUT_LENGTH;
import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_USER_QUERY_LENGTH;

import com.ezaigc.hr.common.web.request.OptionalFilterCode;
import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.request.ValidIdempotencyKey;
import com.ezaigc.hr.identity.application.manager.IdentityAdministrationApplicationManager;
import com.ezaigc.hr.identity.domain.request.IdentityAdminCreateDepartmentRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminCreateUserRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminDeleteDepartmentRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminDeleteUserRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminDisableUserRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminExecuteHandoverRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminHandoverPreviewRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminReasonRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminRoleAssignmentRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminSuperAdminRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminUpdateDepartmentRequest;
import com.ezaigc.hr.identity.domain.request.IdentityAdminUpdateUserRequest;
import com.ezaigc.hr.identity.domain.response.AdminUserResponse;
import com.ezaigc.hr.identity.domain.response.CreatedUserResponse;
import com.ezaigc.hr.identity.domain.response.DepartmentResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchDetailsResponse;
import com.ezaigc.hr.identity.domain.response.HandoverBatchResponse;
import com.ezaigc.hr.identity.domain.response.HandoverPreviewResponse;
import com.ezaigc.hr.identity.domain.response.ResetCredentialResponse;
import com.ezaigc.hr.identity.domain.response.UserOrganizationAssignmentsResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class IdentityAdminController {

    @Resource
    private IdentityAdministrationApplicationManager service;

    @GetMapping("/departments")
    List<DepartmentResponse> departments() {
        return service.departments();
    }

    /**
     * 创建部门。
     */
    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentResponse createDepartment(
            @Valid @RequestBody IdentityAdminCreateDepartmentRequest command) {
        return service.createDepartment(command.getName(), command.getParentDepartmentId());
    }

    /**
     * 更新部门。
     */
    @PatchMapping("/departments/{departmentId}")
    DepartmentResponse updateDepartment(
            @PlatformIdentifier @PathVariable String departmentId,
            @Valid @RequestBody IdentityAdminUpdateDepartmentRequest command) {
        return service.updateDepartment(
                departmentId,
                command.getName(),
                command.getParentDepartmentId(),
                command.getAssignmentQueueOwnerUserId(),
                command.getStatus(),
                parse(command.getVersion()));
    }

    @GetMapping("/users")
    List<AdminUserResponse> users(
            @RequestParam(required = false)
                    @Size(max = MAX_USER_QUERY_LENGTH, message = "query 长度不能超过 {max}")
                    String query,
            @OptionalFilterCode @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return service.users(query, status, limit);
    }

    /**
     * 创建用户。
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedUserResponse createUser(@Valid @RequestBody IdentityAdminCreateUserRequest command) {
        return service.createUser(
                command.getDisplayName(),
                command.getWorkEmail(),
                command.getSourceTitle(),
                command.getHomeDepartmentId(),
                command.getDirectLeaderUserId(),
                command.getSupportDepartmentIds(),
                command.getRoles(),
                command.getReason());
    }

    /**
     * 更新用户。
     */
    @PatchMapping("/users/{userId}")
    AdminUserResponse updateUser(
            @PlatformIdentifier @PathVariable String userId, @Valid @RequestBody IdentityAdminUpdateUserRequest command) {
        return service.updateUser(
                userId,
                command.getDisplayName(),
                command.getWorkEmail(),
                command.getSourceTitle(),
                command.getHomeDepartmentId(),
                command.getDirectLeaderUserId(),
                command.getSupportDepartmentIds(),
                parse(command.getVersion()),
                command.getReason());
    }

    @GetMapping("/users/{userId}/organization-assignments")
    UserOrganizationAssignmentsResponse organizationAssignments(@PlatformIdentifier @PathVariable String userId) {
        return service.organizationAssignments(userId);
    }

    @PostMapping("/users/{userId}/credentials/reset")
    ResetCredentialResponse resetCredential(
            @PlatformIdentifier @PathVariable String userId, @Valid @RequestBody IdentityAdminReasonRequest command) {
        return service.resetCredential(userId, command.getReason());
    }

    /**
     * 停用用户。
     */
    @PostMapping("/users/{userId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableUser(
            @PlatformIdentifier @PathVariable String userId, @Valid @RequestBody IdentityAdminDisableUserRequest command) {
        service.disableUser(
                userId,
                parse(command.getVersion()),
                command.getReason(),
                command.isRetainHistoricalUnresolved());
    }

    /** 删除没有任何引用的部门。 */
    @DeleteMapping("/departments/{departmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDepartment(
            @PlatformIdentifier @PathVariable String departmentId,
            @Valid @RequestBody IdentityAdminDeleteDepartmentRequest command) {
        service.deleteDepartment(departmentId, parse(command.getVersion()), command.getReason());
    }

    /**
     * 逻辑删除用户。
     */
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(
            @PlatformIdentifier @PathVariable String userId,
            @Valid @RequestBody IdentityAdminDeleteUserRequest command) {
        service.deleteUser(userId, parse(command.getVersion()), command.getReason());
    }

    /**
     * 预览交接。
     */
    @PostMapping("/users/{userId}/handover-previews")
    HandoverPreviewResponse previewHandover(
            @PlatformIdentifier @PathVariable String userId,
            @Valid @RequestBody IdentityAdminHandoverPreviewRequest command) {
        return service.previewHandover(userId, command.getTargetUserId());
    }

    /**
     * 交接S。
     */
    @GetMapping("/users/{userId}/handovers")
    List<HandoverBatchResponse> handovers(@PlatformIdentifier @PathVariable String userId) {
        return service.handovers(userId);
    }

    @GetMapping("/users/{userId}/handovers/{batchId}")
    HandoverBatchDetailsResponse handover(@PlatformIdentifier @PathVariable String userId, @PlatformIdentifier @PathVariable String batchId) {
        return service.handover(userId, batchId);
    }

    /**
     * 执行交接。
     */
    @PostMapping("/users/{userId}/handovers")
    @ResponseStatus(HttpStatus.CREATED)
    HandoverBatchDetailsResponse executeHandover(
            @PlatformIdentifier @PathVariable String userId,
            @ValidIdempotencyKey @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody IdentityAdminExecuteHandoverRequest command) {
        return service.executeHandover(
                userId,
                command.getTargetUserId(),
                command.getSelectionMode(),
                command.getSelectedResourceKeys(),
                command.getReason(),
                idempotencyKey);
    }

    @GetMapping("/role-assignments")
    List<String> roleAssignments(@PlatformIdentifier @RequestParam String userId) {
        return service.roles(userId);
    }

    /**
     * 分配角色。
     */
    @PostMapping("/role-assignments")
    List<String> assignRole(@Valid @RequestBody IdentityAdminRoleAssignmentRequest command) {
        return service.assignRole(command.getUserId(), command.getRoleCode(), command.getReason());
    }

    /**
     * 撤销角色。
     */
    @DeleteMapping("/role-assignments/{userId}/{roleCode}")
    List<String> revokeRole(
            @PlatformIdentifier @PathVariable String userId,
            @NotBlank
            @Size(max = MAX_ROLE_CODE_INPUT_LENGTH)
            @Pattern(regexp = ASSIGNABLE_ROLE_CODE_INPUT_PATTERN)
            @PathVariable String roleCode,
            @Valid @RequestBody IdentityAdminReasonRequest command) {
        return service.revokeRole(userId, roleCode, command.getReason());
    }

    @GetMapping("/super-admins")
    List<AdminUserResponse> superAdmins() {
        return service.superAdmins();
    }

    @PostMapping("/super-admins")
    List<String> grantSuperAdmin(@Valid @RequestBody IdentityAdminSuperAdminRequest command) {
        return service.grantSuperAdmin(command.getUserId(), command.getReason());
    }

    /**
     * 撤销超级管理员。
     */
    @DeleteMapping("/super-admins/{userId}")
    List<String> revokeSuperAdmin(
            @PlatformIdentifier @PathVariable String userId, @Valid @RequestBody IdentityAdminReasonRequest command) {
        return service.revokeSuperAdmin(userId, command.getReason());
    }

}

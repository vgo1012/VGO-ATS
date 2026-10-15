package com.ezaigc.hr.identity.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.application.assembler.WecomSyncAssembler;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.domain.entity.AdminUserRecord;
import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentLinkEntity;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import com.ezaigc.hr.identity.domain.request.WecomSyncRequest;
import com.ezaigc.hr.identity.domain.response.WecomStoredDirectoryResponse;
import com.ezaigc.hr.identity.domain.response.WecomSyncBatchResponse;
import com.ezaigc.hr.identity.enums.WecomDirectoryReadStatus;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import com.ezaigc.hr.identity.service.IdentityAdministrationPersistenceService;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import com.ezaigc.hr.identity.service.WecomSyncBatchPersistenceService;
import com.ezaigc.hr.identity.service.WecomSyncStatePersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomSyncApplicationManager {
    @Resource private AuthorizationService authorization;
    @Resource private WecomDirectoryPort directory;
    @Resource private WecomSyncProperties properties;
    @Resource private WecomSyncTransactionManager transactions;
    @Resource private WecomSyncAssembler assembler;
    @Resource private WecomSyncBatchPersistenceService batches;
    @Resource private WecomSyncStatePersistenceService states;
    @Resource private WecomDepartmentPersistenceService departments;
    @Resource private WecomMemberPersistenceService members;
    @Resource private WecomAccountLinkPersistenceService accountLinks;
    @Resource private IdentityAdministrationPersistenceService identityAdministration;
    @Resource private WecomDepartmentLinkPersistenceService departmentLinks;
    @Resource private Clock clock;

    public WecomSyncBatchResponse synchronize(WecomSyncRequest request) {
        requireAdmin();
        String corpId = configuredCorp();
        if (!properties.isEnabled()) throw PlatformExceptions.conflict("WECOM_SYNC_DISABLED", "通讯录落库同步未启用");
        return assembler.batch(transactions.submit(corpId, "manual:" + request.getRequestKey(), "MANUAL",
                authorization.currentActor().getUserId(), request.getRetryOfBatchId()));
    }

    public List<WecomSyncBatchResponse> history(int page) {
        requireAdmin();
        if (page < 1 || page > 10000) throw PlatformExceptions.badRequest("INVALID_PAGE", "页码超出范围");
        String corpId = storedCorp();
        return batches.page(new Page<>(page, 20, false), Wrappers.<WecomSyncBatchEntity>lambdaQuery()
                .eq(WecomSyncBatchEntity::getCorpId, corpId)
                .orderByDesc(WecomSyncBatchEntity::getRequestedAt).orderByDesc(WecomSyncBatchEntity::getId))
                .getRecords().stream().map(assembler::batch).toList();
    }

    public WecomSyncBatchResponse batch(String id) {
        requireAdmin();
        WecomSyncBatchEntity batch = batches.getById(id);
        if (batch == null || !batch.getCorpId().equals(storedCorp())) {
            throw PlatformExceptions.notFound("WECOM_BATCH_NOT_FOUND", "未找到当前企业的同步批次");
        }
        return assembler.batch(batch);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WecomStoredDirectoryResponse stored() {
        requireAdmin();
        String corpId = storedCorp();
        WecomSyncStateEntity state = states.getById(corpId);
        Map<String, String> accountIdByMemberId = accountLinks.list().stream().collect(Collectors.toMap(
                WecomAccountLinkEntity::getWecomMemberId, WecomAccountLinkEntity::getUserAccountId));
        Map<String, AdminUserRecord> usersById = identityAdministration.findUsersByIds(
                accountIdByMemberId.values().stream().distinct().toList()).stream()
                .collect(Collectors.toMap(AdminUserRecord::getId, Function.identity()));
        Map<String, String> localDepartmentIdBySourceId = departmentLinks.list().stream().collect(Collectors.toMap(
                WecomDepartmentLinkEntity::getWecomDepartmentId, WecomDepartmentLinkEntity::getLocalDepartmentId));
        Map<String, DepartmentRecord> localDepartmentsById = identityAdministration.findDepartments().stream()
                .collect(Collectors.toMap(DepartmentRecord::getId, Function.identity()));
        return new WecomStoredDirectoryResponse(corpId, state == null ? null : state.getPublishedBatchId(),
                departments.list(Wrappers.<WecomDepartmentEntity>lambdaQuery().eq(WecomDepartmentEntity::getCorpId, corpId)
                        .orderByAsc(WecomDepartmentEntity::getDepartmentId)).stream()
                        .map(department -> assembler.department(department, localDepartmentsById.get(
                                localDepartmentIdBySourceId.get(department.getId())))).toList(),
                members.list(Wrappers.<WecomMemberEntity>lambdaQuery().eq(WecomMemberEntity::getCorpId, corpId)
                        .orderByAsc(WecomMemberEntity::getUserId)).stream()
                        .map(member -> assembler.member(member,
                                usersById.get(accountIdByMemberId.get(member.getId())))).toList());
    }

    public void scheduleDaily() {
        if (!properties.isEnabled()) return;
        String corpId = configuredCorp();
        String day = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).toString();
        transactions.submit(corpId, "daily:" + day, "SCHEDULED", null, null);
    }

    public String configuredCorp() {
        WecomDirectoryResultDTO status = directory.status();
        if (status.getCorpId() == null || status.getCorpId().isBlank() || status.getCorpId().length() > 128
                || WecomDirectoryReadStatus.DISABLED.matches(status.getStatus()) || WecomDirectoryReadStatus.NOT_CONFIGURED.matches(status.getStatus())) {
            throw PlatformExceptions.conflict("WECOM_NOT_CONFIGURED", "请先启用并配置企业微信通讯录读取");
        }
        return status.getCorpId();
    }

    public String storedCorp() {
        String configured = directory.status().getCorpId();
        if (configured != null && !configured.isBlank() && states.getById(configured) != null) {
            return configured;
        }
        List<WecomSyncStateEntity> published = states.list(Wrappers.<WecomSyncStateEntity>lambdaQuery()
                .isNotNull(WecomSyncStateEntity::getPublishedBatchId).orderByDesc(WecomSyncStateEntity::getUpdateTime));
        if (published.size() == 1) {
            return published.getFirst().getCorpId();
        }
        if (published.isEmpty()) {
            throw PlatformExceptions.notFound("WECOM_DIRECTORY_NOT_PUBLISHED", "尚无已发布的企业微信来源目录");
        }
        throw PlatformExceptions.conflict("WECOM_CORP_REQUIRED", "存在多个已发布企业目录，请配置当前企业 ID");
    }

    private void requireAdmin() {
        if (!authorization.currentActor().isSuperAdmin()) {
            throw PlatformExceptions.forbidden("SUPER_ADMIN_REQUIRED", "需要超级管理员权限");
        }
    }
}

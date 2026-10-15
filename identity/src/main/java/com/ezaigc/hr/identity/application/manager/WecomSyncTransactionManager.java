package com.ezaigc.hr.identity.application.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import com.ezaigc.hr.identity.domain.event.WecomSyncRequested;
import com.ezaigc.hr.identity.enums.WecomSyncStatus;
import com.ezaigc.hr.identity.service.WecomSyncBatchPersistenceService;
import com.ezaigc.hr.identity.service.WecomSyncStatePersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomSyncTransactionManager {
    @Resource private WecomSyncStatePersistenceService states;
    @Resource private WecomSyncBatchPersistenceService batches;
    @Resource private WecomSyncProperties properties;
    @Resource private WecomDirectoryPublishManager publisher;
    @Resource private ApplicationEventPublisher events;
    @Resource private PlatformIdGenerator ids;

    @Transactional
    public WecomSyncBatchEntity submit(String corpId, String key, String trigger, String actorId, String retryOf) {
        states.ensure(corpId);
        WecomSyncStateEntity state = states.lock(corpId);
        Instant now = states.databaseNow();
        WecomSyncBatchEntity previous = batches.getOne(Wrappers.<WecomSyncBatchEntity>lambdaQuery()
                .eq(WecomSyncBatchEntity::getCorpId, corpId).eq(WecomSyncBatchEntity::getRequestKey, key));
        if (previous != null) {
            if (!Objects.equals(previous.getRetryOfBatchId(), retryOf) || !previous.getTriggerType().equals(trigger)) {
                throw PlatformExceptions.conflict("IDEMPOTENCY_CONFLICT", "请求键已用于其他同步操作");
            }
            return previous;
        }
        expire(state, now);
        if (state.getActiveBatchId() != null) {
            throw PlatformExceptions.conflict("WECOM_SYNC_RUNNING", "已有通讯录同步批次，请查看该批次结果");
        }
        if (state.getNextAllowedAt() != null && state.getNextAllowedAt().isAfter(now)) {
            throw PlatformExceptions.conflict("WECOM_SYNC_COOLDOWN", "通讯录同步正在冷却，请稍后重试");
        }
        if (retryOf != null) {
            WecomSyncBatchEntity original = batches.getById(retryOf);
            if (original == null || !corpId.equals(original.getCorpId())
                    || !(WecomSyncStatus.FAILED.matches(original.getStatus()) || WecomSyncStatus.INTERRUPTED.matches(original.getStatus()))) {
                throw PlatformExceptions.conflict("WECOM_RETRY_NOT_ALLOWED", "只能重试当前企业已失败或中断的同步批次");
            }
        }
        WecomSyncBatchEntity batch = new WecomSyncBatchEntity();
        batch.setId(ids.newId());
        batch.setCorpId(corpId);
        batch.setRequestKey(key);
        batch.setTriggerType(trigger);
        batch.setActorUserId(actorId);
        batch.setRetryOfBatchId(retryOf);
        batch.setScopeVersion(properties.getScopeVersion());
        batch.setStatus(WecomSyncStatus.QUEUED.getCode());
        batch.setRequestedAt(now);
        batch.setLeaseUntil(now.plusSeconds(properties.getLeaseSeconds()));
        batch.setSafeMessage("等待执行，尚未修改已发布目录");
        requireWrite(batches.save(batch));
        state.setActiveBatchId(batch.getId());
        state.setNextAllowedAt(now.plusSeconds(properties.getCooldownSeconds()));
        requireWrite(states.updateById(state));
        events.publishEvent(new WecomSyncRequested(corpId));
        return batch;
    }

    @Transactional
    public WecomSyncBatchEntity claim(String corpId) {
        WecomSyncStateEntity state = states.lock(corpId);
        if (state == null) return null;
        Instant now = states.databaseNow();
        expire(state, now);
        if (state.getActiveBatchId() == null) return null;
        WecomSyncBatchEntity batch = batches.getById(state.getActiveBatchId());
        if (!WecomSyncStatus.QUEUED.matches(batch.getStatus())) return null;
        batch.setStatus(WecomSyncStatus.RUNNING.getCode());
        batch.setStartedAt(now);
        batch.setLeaseUntil(now.plusSeconds(properties.getLeaseSeconds()));
        batch.setRunToken(UUID.randomUUID().toString());
        batch.setSafeMessage("正在读取并校验完整通讯录，尚未发布");
        requireWrite(batches.updateById(batch));
        return batch;
    }

    @Transactional(timeout = 120)
    public void complete(WecomSyncBatchEntity claimed, WecomDirectoryResultDTO snapshot) {
        WecomSyncStateEntity state = states.lock(claimed.getCorpId());
        Instant now = states.databaseNow();
        WecomSyncBatchEntity batch = owned(state, claimed, now);
        if (batch == null) return;
        if (!batch.getScopeVersion().equals(properties.getScopeVersion())) {
            finish(state, batch, WecomSyncStatus.FAILED, "SCOPE_VERSION_CHANGED", "同步期间范围配置已变更，请重新同步", now);
            return;
        }
        publisher.publish(snapshot, batch);
        state.setPublishedBatchId(batch.getId());
        finish(state, batch, batch.getMissingCount() == 0 ? WecomSyncStatus.SUCCEEDED : WecomSyncStatus.WARNING, null,
                batch.getMissingCount() == 0 ? "目录已发布；未开通登录、修改密码或授予权限"
                        : "目录已更新；未出现人员标记待核实，未推断离职或修改登录权限", states.databaseNow());
    }

    @Transactional
    public void fail(WecomSyncBatchEntity claimed, String code, String message) {
        WecomSyncStateEntity state = states.lock(claimed.getCorpId());
        Instant now = states.databaseNow();
        WecomSyncBatchEntity batch = owned(state, claimed, now);
        if (batch != null) finish(state, batch, WecomSyncStatus.FAILED, code, message, now);
    }

    private WecomSyncBatchEntity owned(WecomSyncStateEntity state, WecomSyncBatchEntity claimed, Instant now) {
        if (state == null || !claimed.getId().equals(state.getActiveBatchId())) return null;
        WecomSyncBatchEntity batch = batches.getById(claimed.getId());
        if (!WecomSyncStatus.RUNNING.matches(batch.getStatus()) || !Objects.equals(batch.getRunToken(), claimed.getRunToken())) return null;
        if (!batch.getLeaseUntil().isAfter(now)) {
            expire(state, now);
            return null;
        }
        return batch;
    }

    private void expire(WecomSyncStateEntity state, Instant now) {
        if (state.getActiveBatchId() == null) return;
        WecomSyncBatchEntity batch = batches.getById(state.getActiveBatchId());
        if (batch != null && !batch.getLeaseUntil().isAfter(now)) {
            finish(state, batch, WecomSyncStatus.INTERRUPTED, "LEASE_EXPIRED", "执行中断或租约超时；上次成功目录保留，可发起新批次重试", now);
        }
    }

    private void finish(WecomSyncStateEntity state, WecomSyncBatchEntity batch, WecomSyncStatus status,
                        String code, String message, Instant now) {
        batch.setStatus(status.getCode());
        batch.setErrorCode(code);
        batch.setSafeMessage(message);
        batch.setFinishedAt(now);
        requireWrite(batches.updateById(batch));
        state.setActiveBatchId(null);
        state.setNextAllowedAt(now.plusSeconds(properties.getCooldownSeconds()));
        requireWrite(states.updateById(state));
    }

    private void requireWrite(boolean written) {
        if (!written) throw new IllegalStateException("wecom-sync-write-conflict");
    }
}

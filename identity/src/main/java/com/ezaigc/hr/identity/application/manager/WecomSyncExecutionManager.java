package com.ezaigc.hr.identity.application.manager;

import com.ezaigc.hr.common.infrastructure.logging.RepeatedFailureLogger;
import com.ezaigc.hr.identity.api.WecomDirectoryResultDTO;
import com.ezaigc.hr.identity.application.provider.WecomDirectoryPort;
import com.ezaigc.hr.identity.application.support.WecomSnapshotRejectedException;
import com.ezaigc.hr.identity.config.WecomSyncProperties;
import com.ezaigc.hr.identity.domain.entity.WecomSyncBatchEntity;
import com.ezaigc.hr.identity.enums.WecomDirectoryReadStatus;
import jakarta.annotation.Resource;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WecomSyncExecutionManager {
    @Resource private WecomDirectoryPort directory;
    @Resource private WecomSyncTransactionManager transactions;
    @Resource private WecomSyncProperties properties;
    private final RepeatedFailureLogger failures = new RepeatedFailureLogger(LoggerFactory.getLogger(WecomSyncExecutionManager.class));

    public void execute(String corpId) {
        if (!properties.isEnabled()) return;
        WecomSyncBatchEntity batch = transactions.claim(corpId);
        if (batch == null) return;
        try {
            WecomDirectoryResultDTO result = directory.preview();
            if (!WecomDirectoryReadStatus.PREVIEW_READY.matches(result.getStatus())) {
                transactions.fail(batch, result.getStatus(), result.getMessage());
                return;
            }
            transactions.complete(batch, result);
            failures.recovered("wecom-directory-sync");
        } catch (WecomSnapshotRejectedException rejected) {
            transactions.fail(batch, rejected.getCode(), rejected.getMessage());
        } catch (RuntimeException failure) {
            failures.failed("wecom-directory-sync", failure);
            transactions.fail(batch, "SYNC_UNAVAILABLE", "同步处理失败；已回滚目录变更，请稍后重试并查看服务器诊断");
        }
    }
}

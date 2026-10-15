package com.ezaigc.hr.recruiting.demand.application.manager;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.core.util.CommonUtils;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import com.ezaigc.hr.recruiting.demand.service.JdParsingPersistenceService;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class JdFactPersistenceManager {

    @Resource
    private PlatformIdGenerator ids;

    @Resource
    private Clock clock;

    @Resource
    private JdParsingPersistenceService persistence;

    @Transactional
    public JdFactSnapshotEntity store(
            String versionId, String modelRunId, String schemaVersion, String factsJson) {
        return store(versionId, modelRunId, schemaVersion, factsJson, null);
    }

    @Transactional
    public JdFactSnapshotEntity store(
            String versionId, String modelRunId, String schemaVersion, String factsJson, String sourceSnapshotId) {
        JdFactSnapshotEntity previous = persistence.findCurrentForUpdate(versionId);
        Instant now = clock.instant();
        int expectedClearedRows = previous == null ? 0 : 1;
        int clearedRows = persistence.clearCurrent(versionId, now);
        requireAffectedRows(clearedRows, expectedClearedRows, "清除当前 JD 事实快照", versionId);
        String id = ids.newId();
        int insertedRows =
                persistence.insertSnapshot(
                        id,
                        versionId,
                        modelRunId,
                        previous == null ? 1 : previous.getSnapshotVersion() + 1,
                        schemaVersion,
                        factsJson,
                        sourceSnapshotId,
                        now);
        requireAffectedRows(insertedRows, 1, "创建 JD 事实快照", versionId);
        JdFactSnapshotEntity snapshot = persistence.findSnapshot(id);
        if (snapshot == null || CommonUtils.isBlank(snapshot.getId()) || !id.equals(snapshot.getId())) {
            throw new IllegalStateException("JD 事实快照写入后无法按标识回读, jdVersionId=" + versionId);
        }
        return snapshot;
    }

    private static void requireAffectedRows(
            int actualRows, int expectedRows, String operation, String versionId) {
        if (actualRows != expectedRows) {
            throw new IllegalStateException(
                    operation
                            + "影响行数异常, jdVersionId="
                            + versionId
                            + ", expectedRows="
                            + expectedRows
                            + ", actualRows="
                            + actualRows);
        }
    }
}

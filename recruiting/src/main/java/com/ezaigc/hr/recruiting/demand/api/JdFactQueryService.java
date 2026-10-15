package com.ezaigc.hr.recruiting.demand.api;

import java.util.Collection;

public interface JdFactQueryService {
    int MAX_READ_ACCESS_BATCH_SIZE = 200;
    JdFactSnapshotData currentFacts(String jdVersionId);

    /** 只读业务复查当前事实与实时 JD 权限，不获取新业务使用锁。 */
    void requireCurrentFactsReadAccess(String jdVersionId);

    /** 批量复查当前事实存在性及实时权限；最多 200 项，不解析事实 JSON 或获取新业务锁。 */
    void requireCurrentFactsReadAccessBatch(Collection<String> jdVersionIds);

    JdFactSnapshotData factsBySnapshotId(String snapshotId);
}

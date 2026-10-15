package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Map;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 简历事实查询业务服务契约。
 */
public interface ResumeFactQueryService {

    ResumeFactSnapshotData currentFacts(String resumeVersionId);

    ResumeFactSnapshotData factsBySnapshotId(String snapshotId);

    /**
     * 批量预装载当前事实，随后按简历版本 ID 获取。
     *
     * <p>单次最多 1,000 项。批量阶段使用固定数量查询；单条事实缺失、孤立或损坏时，访问该项会回退单条查询并保留原失败语义。
     */
    ResumeFactBatch currentFactBatch(Collection<String> resumeVersionIds);

    /**
     * 批量预装载不可变事实快照，随后按快照 ID 获取。
     *
     * <p>单次最多 1,000 项。数据库级批量失败由调用任务整体重试，单条数据错误仍在访问该项时隔离。
     */
    ResumeFactBatch snapshotFactBatch(Collection<String> snapshotIds);

    /**
     * 批量查询有效候选人的最新可用简历当前快照及联系方式，用于搜索投影回查。
     *
     * <p>最多 1,000 个版本 ID；旧版本及最新版本尚无事实时不返回。返回值以简历版本 ID 为键；
     * 联系方式不得复制进可重建的搜索索引。历史事实读取继续使用独立快照 API。
     */
    Map<String, CurrentFactContact> currentFactContacts(Collection<String> resumeVersionIds);

    /**
     * 当前事实快照对应的联系方式。
     */
    @Value
    @Accessors(fluent = true)
    class CurrentFactContact {

        String snapshotId;
        @ToString.Exclude
        String email;
        @ToString.Exclude
        String phone;
    }

    /**
     * 一次批量预装载结果的按键访问器。
     */
    @FunctionalInterface
    interface ResumeFactBatch {

        ResumeFactSnapshotData get(String id);
    }
}

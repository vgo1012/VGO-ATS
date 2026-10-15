package com.ezaigc.hr.talent.api;

import java.time.Instant;
import lombok.ToString;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/** 搜索工作只读取候选人当前状态，包含删除记录及尚无事实的最新可用简历。 */
public interface ResumeSearchStateSource {
    State currentState(String candidateId);

    /** 按候选人 ID 回放当前状态；包含删除和无事实记录，每页最多 500 项。 */
    StatePage currentStatesAfter(String afterCandidateId, int limit);

    @Value
    @Accessors(fluent = true)
    class StatePage {
        List<State> items;
        boolean hasMore;
    }

    @Value
    @Accessors(fluent = true)
    class State {
        String candidateId;
        long candidateVersion;
        @ToString.Exclude String fullName;
        @ToString.Exclude String email;
        @ToString.Exclude String phone;
        String resumeVersionId;
        int resumeVersionNo;
        boolean deleted;
        ResumeFactSnapshotData facts;
        Instant updateTime;
        String sourceChannel;
    }
}

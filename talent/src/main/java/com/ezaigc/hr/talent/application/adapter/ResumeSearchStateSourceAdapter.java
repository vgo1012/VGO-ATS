package com.ezaigc.hr.talent.application.adapter;

import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import com.ezaigc.hr.talent.domain.dto.ResumeSearchStateRow;
import com.ezaigc.hr.talent.enums.CandidateStatus;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 状态行与不可变事实正文使用同一数据库快照，避免读取途中纠正或合并产生混合内容。 */
@Service
public class ResumeSearchStateSourceAdapter implements ResumeSearchStateSource {
    @Resource private ResumeParsingPersistenceService persistence;
    @Resource private ResumeFactQueryService facts;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public State currentState(String candidateId) {
        if (candidateId == null || candidateId.isBlank() || candidateId.length() > 26) {
            throw new IllegalArgumentException("a candidate identifier is required");
        }
        var row = persistence.findCurrentSearchState(candidateId);
        if (row == null) return null;
        var snapshot = CandidateStatus.DELETED.matches(row.getCandidateStatus()) || row.getFactSnapshotId() == null
                ? null : facts.factsBySnapshotId(row.getFactSnapshotId());
        return state(row, snapshot);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatePage currentStatesAfter(String afterCandidateId, int limit) {
        if (afterCandidateId == null || afterCandidateId.length() > 26 || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("a bounded candidate cursor page is required");
        }
        var loaded = persistence.findCurrentSearchStatesAfter(afterCandidateId, limit + 1);
        var rows = loaded.stream().limit(limit).toList();
        var snapshotIds = rows.stream()
                .filter(row -> CandidateStatus.ACTIVE.matches(row.getCandidateStatus()) && row.getFactSnapshotId() != null)
                .map(ResumeSearchStateRow::getFactSnapshotId).toList();
        var batch = snapshotIds.isEmpty() ? null : facts.snapshotFactBatch(snapshotIds);
        var states = rows.stream().map(row -> state(row,
                batch == null || row.getFactSnapshotId() == null || CandidateStatus.DELETED.matches(row.getCandidateStatus())
                        ? null : batch.get(row.getFactSnapshotId()))).toList();
        return new StatePage(states, loaded.size() > limit);
    }

    private State state(ResumeSearchStateRow row, ResumeFactSnapshotData snapshot) {
        boolean deleted = CandidateStatus.DELETED.matches(row.getCandidateStatus());
        if (!deleted && !CandidateStatus.ACTIVE.matches(row.getCandidateStatus())) {
            throw new IllegalStateException("unsupported candidate search state");
        }
        if (!deleted && row.getFactSnapshotId() != null && snapshot == null) {
            throw new IllegalStateException("current search facts could not be loaded");
        }
        return new State(row.getCandidateId(), row.getCandidateVersion(), row.getFullName(), row.getEmail(), row.getPhone(),
                row.getResumeVersionId(), row.getResumeVersionNo(), deleted,
                snapshot, row.getUpdateTime(), row.getSourceChannel());
    }
}

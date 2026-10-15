package com.ezaigc.hr.talent.application.adapter;

import com.ezaigc.hr.talent.api.ResumeFactProjectionSource;
import com.ezaigc.hr.talent.api.ResumeFactQueryService;
import com.ezaigc.hr.talent.service.ResumeParsingPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeFactProjectionSourceAdapter implements ResumeFactProjectionSource {

  @Resource private ResumeFactQueryService facts;

  @Resource private ResumeParsingPersistenceService persistence;

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ProjectionPage currentFactsAfter(
      Instant afterCreatedAt, String afterSnapshotId, int limit) {
    if (afterCreatedAt == null || afterSnapshotId == null || limit < 1 || limit > 500) {
      throw new IllegalArgumentException("projection cursor and limit are required");
    }
    var loaded = persistence.findCurrentFactSnapshotsAfter(afterCreatedAt, afterSnapshotId, limit + 1);
    boolean more = loaded.size() > limit;
    var pageRows = loaded.stream().limit(limit).toList();
    var factBatch = facts.snapshotFactBatch(pageRows.stream().map(row -> row.getId()).toList());
    var items =
        pageRows.stream()
            .map(
                snapshot ->
                    new ProjectionItem(
                        factBatch.get(snapshot.getId()),
                        snapshot.getCreateTime(),
                        snapshot.getSourceChannel(), snapshot.getResumeVersionNo(), snapshot.getCandidateVersion(),
                        snapshot.getFullName(), snapshot.getEmail(), snapshot.getPhone()))
            .toList();
    return new ProjectionPage(items, more);
  }
}

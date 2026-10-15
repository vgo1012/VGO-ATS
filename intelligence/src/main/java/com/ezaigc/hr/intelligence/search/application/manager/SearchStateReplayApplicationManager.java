package com.ezaigc.hr.intelligence.search.application.manager;

import static com.ezaigc.hr.common.core.constant.PlatformActorConstants.SYSTEM_ACTOR_ID;
import static com.ezaigc.hr.common.core.constant.PlatformIdConstants.MIN_ID;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_PROJECTION_PAGE_SIZE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.RESUME_FACTS_PROJECTION;

import com.ezaigc.hr.intelligence.search.application.assembler.SearchDocumentFactory;
import com.ezaigc.hr.intelligence.search.application.provider.SearchIndexPort;
import com.ezaigc.hr.intelligence.search.domain.entity.StateReplayCheckpoint;
import com.ezaigc.hr.intelligence.search.service.StateReplayCheckpointService;
import com.ezaigc.hr.talent.api.ResumeSearchStateSource;
import jakarta.annotation.Resource;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 激活索引后回放全候选人当前状态；崩溃只会重投未推进的一页。 */
@Component
public class SearchStateReplayApplicationManager {
  @Resource private ResumeSearchStateSource source;
  @Resource private SearchDocumentFactory factory;
  @Resource private SearchIndexPort index;
  @Resource private SearchIndexAuthorityApplicationManager authority;
  @Resource private StateReplayCheckpointService checkpoints;
  @Resource private Clock clock;
  private boolean initialized;
  private boolean resetMemoryIndex;

  /** 不允许网络写入占用调用者事务；人才模块在自己的短只读事务内批量读取。 */
  @Transactional(propagation = Propagation.NEVER)
  public synchronized int replayNext(int limit) {
    initialize();
    var checkpoint = loadCheckpoint();
    // 必须先读 observed 版本再读批准行；批准事务会使旧页的版本失效。
    var approved = authority.currentTarget();
    String target = approved.indexName();
    if (resetMemoryIndex || !target.equals(checkpoint.getIndexName())) {
      if (!checkpoints.bindIndex(target, checkpoint.getVersion(), SYSTEM_ACTOR_ID, clock.instant())) return 0;
      resetMemoryIndex = false;
      checkpoint.setIndexName(target);
      checkpoint.setLastCandidateId(MIN_ID);
      checkpoint.setCompleted(false);
      checkpoint.setVersion(checkpoint.getVersion() + 1);
    }
    if (checkpoint.isCompleted()) return 0;
    var page = source.currentStatesAfter(checkpoint.getLastCandidateId(), Math.clamp(limit, 1, MAX_PROJECTION_PAGE_SIZE));
    if (!page.items().isEmpty()) {
      index.upsertBatch(approved, page.items().stream().map(factory::create).toList());
    }
    String last = page.items().isEmpty() ? checkpoint.getLastCandidateId() : page.items().getLast().candidateId();
    if (!checkpoints.advance(last, !page.hasMore(), page.items().size(), checkpoint.getVersion(), SYSTEM_ACTOR_ID, clock.instant())) {
      throw new IllegalStateException("search state replay checkpoint changed");
    }
    return page.items().size();
  }

  private void initialize() {
    if (initialized) return;
    initializeCheckpoint();
    index.ensureApprovedIndex(authority.currentTarget());
    resetMemoryIndex = !index.durable();
    initialized = true;
  }

  private StateReplayCheckpoint loadCheckpoint() {
    StateReplayCheckpoint checkpoint = checkpoints.checkpoint();
    if (checkpoint != null) return checkpoint;
    // 数据库重新建表或游标行被清理后，进程内初始化标志仍可能为 true；幂等补建后从头回放。
    initializeCheckpoint();
    checkpoint = checkpoints.checkpoint();
    if (checkpoint == null) {
      throw new IllegalStateException("search state replay checkpoint unavailable");
    }
    return checkpoint;
  }

  private void initializeCheckpoint() {
    var checkpoint = new StateReplayCheckpoint();
    checkpoint.setProjectionName(RESUME_FACTS_PROJECTION);
    checkpoint.setLastCandidateId(MIN_ID);
    checkpoint.setCreateId(SYSTEM_ACTOR_ID);
    checkpoint.setUpdateId(SYSTEM_ACTOR_ID);
    var now = clock.instant();
    checkpoint.setCreateTime(now);
    checkpoint.setUpdateTime(now);
    checkpoints.initialize(checkpoint);
  }
}

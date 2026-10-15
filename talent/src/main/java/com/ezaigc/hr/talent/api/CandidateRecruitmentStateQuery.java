package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import lombok.Value;

/** 招聘模块提供候选人的流程状态；人才模块不读取招聘表。 */
public interface CandidateRecruitmentStateQuery {
  /** 合并调用已持有候选人锁，按候选人当前读全部招聘及推荐业务历史。 */
  boolean hasMergeProtectedHistory(String candidateId);

  Map<String, List<State>> states(Collection<String> candidateIds);

  /** 路由命令已持有简历版本锁时，以当前读检查全部招聘历史（含已终止流程）。 */
  boolean hasRecruitmentHistory(String resumeVersionId);

  /** 只检查仍在进行的招聘流程；终态历史不得阻断新的 JD 评分周期。 */
  boolean hasActiveRecruitment(String resumeVersionId);

  @Value
  class State {
    String resumeVersionId;
    String ownerUserId;
    String stage;
    /** 非终止流程均阻断共享；false 表示该招聘流程已终止。 */
    boolean blocksLibrary;
    boolean screenedOut;
    String applicationId;
    String jdVersionId;
    boolean blacklistRecorded;
    Instant terminalAt;

    public State(
        String resumeVersionId,
        String ownerUserId,
        String stage,
        boolean blocksLibrary,
        boolean screenedOut) {
      this(resumeVersionId, ownerUserId, stage, blocksLibrary, screenedOut, null, null);
    }

    public State(
        String resumeVersionId,
        String ownerUserId,
        String stage,
        boolean blocksLibrary,
        boolean screenedOut,
        String applicationId,
        String jdVersionId) {
      this(resumeVersionId, ownerUserId, stage, blocksLibrary, screenedOut, applicationId, jdVersionId, false);
    }

    public State(String resumeVersionId, String ownerUserId, String stage, boolean blocksLibrary,
        boolean screenedOut, String applicationId, String jdVersionId, boolean blacklistRecorded) {
      this(resumeVersionId, ownerUserId, stage, blocksLibrary, screenedOut, applicationId,
          jdVersionId, blacklistRecorded, null);
    }

    public State(String resumeVersionId, String ownerUserId, String stage, boolean blocksLibrary,
        boolean screenedOut, String applicationId, String jdVersionId, boolean blacklistRecorded,
        Instant terminalAt) {
      this.resumeVersionId = resumeVersionId;
      this.ownerUserId = ownerUserId;
      this.stage = stage;
      this.blocksLibrary = blocksLibrary;
      this.screenedOut = screenedOut;
      this.applicationId = applicationId;
      this.jdVersionId = jdVersionId;
      this.blacklistRecorded = blacklistRecorded;
      this.terminalAt = terminalAt;
    }
  }
}

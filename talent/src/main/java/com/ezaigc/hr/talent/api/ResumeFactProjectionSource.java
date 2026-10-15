package com.ezaigc.hr.talent.api;

import java.time.Instant;
import lombok.ToString;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 简历事实投影来源 接口，定义对外可用的能力契约。
 *
 * <p>仅返回有效候选人的最新可用简历及其当前事实。时间游标用于有界遍历，不代表事务提交顺序。
 */
public interface ResumeFactProjectionSource {
  String TASK_TYPE = "RESUME_SEARCH_PROJECT";

  ProjectionPage currentFactsAfter(Instant afterCreatedAt, String afterSnapshotId, int limit);

  @Value
  @Accessors(fluent = true)
  class ProjectionItem {
    ResumeFactSnapshotData facts;
    Instant createTime;
    String sourceChannel;
    int resumeVersionNo;
    long candidateVersion;
    @ToString.Exclude String fullName;
    @ToString.Exclude String email;
    @ToString.Exclude String phone;

    public ProjectionItem(ResumeFactSnapshotData facts, Instant createTime, String sourceChannel,
        int resumeVersionNo) {
      this(facts, createTime, sourceChannel, resumeVersionNo, 0, null, null, null);
    }

    public ProjectionItem(ResumeFactSnapshotData facts, Instant createTime, String sourceChannel,
        int resumeVersionNo, long candidateVersion, String fullName, String email, String phone) {
      this.facts = facts;
      this.createTime = createTime;
      this.sourceChannel = sourceChannel;
      this.resumeVersionNo = resumeVersionNo;
      this.candidateVersion = candidateVersion;
      this.fullName = fullName;
      this.email = email;
      this.phone = phone;
    }
  }

  @Value
  @Accessors(fluent = true)
  class ProjectionPage {
    List<ProjectionItem> items;
    boolean hasMore;

    public ProjectionPage(List<ProjectionItem> items, boolean hasMore) {
      this.items = List.copyOf(items);
      this.hasMore = hasMore;
    }
  }
}

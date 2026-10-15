package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Map;
import lombok.Value;

/** 岗位模块提供可见的岗位上下文；人才模块不读取需求表。 */
public interface JobContextQuery {
  /** 单次批量查询上限；完整历史由调用方分批读取并合并。 */
  int MAX_VERSIONS_PER_BATCH = 1000;

  Context requireVisibleVersion(String jdVersionId);

  /** 在调用方写事务中锁定逻辑 JD；与删除串行，拒绝已删除或无权使用的岗位。 */
  void requireVersionForNewUse(String jdVersionId);

  Map<String, Context> visibleVersions(Collection<String> jdVersionIds);

  /** sharedJdVersionIds 仅由调用方已回查的共享候选人关联事实产生。 */
  default Map<String, Context> visibleVersions(Collection<String> jdVersionIds, Collection<String> sharedJdVersionIds) {
    return visibleVersions(jdVersionIds);
  }

  @Value
  class Context {
    String jdId;
    String jdVersionId;
    String title;
    String jdCode;
    int versionNo;
  }
}

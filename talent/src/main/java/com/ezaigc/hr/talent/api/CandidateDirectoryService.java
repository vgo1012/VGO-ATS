package com.ezaigc.hr.talent.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 候选人目录业务服务契约。
 *
 * Cross-module validation boundary for commands that target a shared candidate.
 */
public interface CandidateDirectoryService {
  CandidateRef requireActive(String candidateId);

  /** 新事实写入使用：在调用方事务中锁定并读取精确候选人当前状态，不追随合并别名。 */
  CandidateRef lockActive(String candidateId);

  /** 仅供调用方已鉴权的面试批次读取在用候选人姓名，最多200项。 */
  Map<String, String> namesForAuthorizedCandidates(Collection<String> candidateIds);

  /** 仅供调用方已鉴权的招聘活动批次匹配当前姓名和联系方式，不返回敏感字段。 */
  Set<String> matchingCandidateIds(Collection<String> candidateIds, String query);

  /** 与结构化事实详情相同的对象权限，用于阻止筛选泄露不可读事实。 */
  Set<String> factReadableCandidateIds(Collection<String> candidateIds);

  /** 批量核对当前归属人；只返回在用候选人，不依据历史操作者或管理范围推断归属。 */
  Set<String> ownedCandidateIds(Collection<String> candidateIds, String ownerUserId);

  @Value
  @Accessors(fluent = true)
  class CandidateRef {
    String candidateId;
    String fullName;
  }
}

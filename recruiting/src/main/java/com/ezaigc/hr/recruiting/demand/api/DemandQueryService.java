package com.ezaigc.hr.recruiting.demand.api;

import java.util.Collection;
import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;

public interface DemandQueryService {

  JdVersionSnapshot requireJdVersion(String jdVersionId);

  /** 批量读取历史 JD 版本快照，按首次出现的版本 ID 返回。最多 200 项。 */
  Map<String, JdVersionSnapshot> requireJdVersions(Collection<String> jdVersionIds);

  /** 批量读取历史版本对应客户；新内部版本保留 null，旧版本沿用逻辑 JD 客户。最多200项。 */
  Map<String, String> clientIdsForVersions(Collection<String> jdVersionIds);

  /** 授权后的当前页批量补充客户名称，沿用历史版本客户规则；最多200项，缺失客户保留 ID。 */
  Map<String, JdClientSummary> clientSummariesForVersions(Collection<String> jdVersionIds);

  /** 新建业务必须在调用方短事务内锁定 JD 当前行，直到新业务写入提交。 */
  JdVersionSnapshot requireJdVersionForNewUse(String jdVersionId);

  /** HC 写路径按统一顺序锁定逻辑 JD；历史流程终止时允许 JD 已删除。 */
  JdVersionSnapshot lockJdForHeadcount(String jdVersionId);

  JdVersionSnapshot requireCurrentJdVersionForDemand(String demandId);

  boolean hasActiveWorkflow(String jdVersionId);

  ActiveWorkflowSnapshot requireActiveWorkflow(String jdVersionId);

  SubmissionDemandContext requireSubmissionContext(String demandId);

  SubmissionDemandContext requireSubmissionContextForJdVersion(String jdVersionId);

  @Value
  @Accessors(fluent = true)
  class SubmissionDemandContext {
    String demandId;
    String clientId;
    String projectId;
    String positionId;
    String recruitmentType;
    String ownerUserId;
    String ownerDepartmentId;
  }
}

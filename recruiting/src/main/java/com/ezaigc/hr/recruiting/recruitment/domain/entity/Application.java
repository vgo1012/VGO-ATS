package com.ezaigc.hr.recruiting.recruitment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("recruitment_application")
public class Application extends StandardPersistenceFields {
  public Application(String id, String candidateId, String demandId, String jdId, String jdVersionId, String resumeVersionId, String workflowTemplateVersionId, String matchPlanVersionId, String recruitmentType, String currentStage, String outcome, String ownerUserId, String ownerDepartmentId, Long activeSlot, Instant terminalAt, long version) {
    this.id = id;
    this.candidateId = candidateId;
    this.demandId = demandId;
    this.jdId = jdId;
    this.jdVersionId = jdVersionId;
    this.resumeVersionId = resumeVersionId;
    this.workflowTemplateVersionId = workflowTemplateVersionId;
    this.matchPlanVersionId = matchPlanVersionId;
    this.recruitmentType = recruitmentType;
    this.currentStage = currentStage;
    this.outcome = outcome;
    this.ownerUserId = ownerUserId;
    this.ownerDepartmentId = ownerDepartmentId;
    this.activeSlot = activeSlot;
    this.terminalAt = terminalAt;
    this.version = version;
  }

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 候选人 ID。 */
    @TableField("candidate_id")
    private String candidateId;
    /** 招聘需求 ID。 */
    @TableField("demand_id")
    private String demandId;
    /** 职位描述（JD）ID。 */
    @TableField("jd_id")
    private String jdId;
    /** 职位描述（JD）版本 ID。 */
    @TableField("jd_version_id")
    private String jdVersionId;
    /** 简历版本 ID。 */
    @TableField("resume_version_id")
    private String resumeVersionId;
    /** 招聘流程模板版本 ID。 */
    @TableField("workflow_template_version_id")
    private String workflowTemplateVersionId;
    /** 匹配方案版本 ID。 */
    @TableField("match_plan_version_id")
    private String matchPlanVersionId;
    /** 招聘类型。 */
    @TableField("recruitment_type")
    private String recruitmentType;
    @TableField("progressive_follow_up")
    private boolean progressiveFollowUp;

    /** 当前招聘阶段。 */
    @TableField("current_stage")
    private String currentStage;
    /** 流程结果。 */
    @TableField("outcome")
    private String outcome;
    /** 业务负责人用户 ID。 */
    @TableField("owner_user_id")
    private String ownerUserId;
    /** 业务归属部门 ID。 */
    @TableField("owner_department_id")
    private String ownerDepartmentId;
    /** 当前生效版本占位标记。 */
    @TableField("active_slot")
    private Long activeSlot;
    /** 进入终态的时间。 */
    @TableField("terminal_at")
    private Instant terminalAt;
    /** 技术乐观锁版本号。 */
    @Version
    @TableField("version")
    private long version;

}

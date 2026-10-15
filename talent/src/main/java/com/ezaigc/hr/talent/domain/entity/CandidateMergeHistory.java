package com.ezaigc.hr.talent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("talent_candidate_merge_history")
public class CandidateMergeHistory extends StandardVersionedPersistenceFields {

    /**
     * 主键 ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 合并后保留的候选人 ID。
     */
    @TableField("survivor_candidate_id")
    private String survivorCandidateId;
    /**
     * 被合并的候选人 ID。
     */
    @TableField("merged_candidate_id")
    private String mergedCandidateId;

    /** 触发本次合并的身份冲突记录 ID。 */
    @TableField("identity_conflict_id")
    private String identityConflictId;
    /**
     * 保留版本。
     */
    @TableField("survivor_version")
    private long survivorVersion;
    /**
     * 被合并版本。
     */
    @TableField("merged_version")
    private long mergedVersion;
    /**
     * 操作人用户 ID。
     */
    @TableField("operated_by_user_id")
    private String operatedByUserId;
    /**
     * 操作原因。
     */
    @TableField("reason")
    private String reason;
}

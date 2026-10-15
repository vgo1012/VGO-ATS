package com.ezaigc.hr.recruiting.recruitment.domain.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ezaigc.hr.common.infrastructure.persistence.StandardVersionedPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("recruitment_follow_up_plan_history")
public class FollowUpPlanHistory extends StandardVersionedPersistenceFields {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  @TableField("plan_id") private String planId;
  @TableField("action") private String action;
  @TableField("before_snapshot") private String beforeSnapshot;
  @TableField("after_snapshot") private String afterSnapshot;
  @TableField("reason") private String reason;
}

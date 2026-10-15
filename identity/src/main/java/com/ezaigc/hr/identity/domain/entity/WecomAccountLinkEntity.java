package com.ezaigc.hr.identity.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_wecom_account_link")
public class WecomAccountLinkEntity extends StandardPersistenceFields {
    @TableId("id")
    private String id;
    @TableField("wecom_member_id")
    private String wecomMemberId;
    @TableField("user_account_id")
    private String userAccountId;
    @TableField("linked_by_user_id")
    private String linkedByUserId;
    @Version
    @TableField("version")
    private int version;
}

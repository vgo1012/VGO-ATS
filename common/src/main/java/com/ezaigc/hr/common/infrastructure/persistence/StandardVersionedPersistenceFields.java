package com.ezaigc.hr.common.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 在共享审计字段上增加数据库技术乐观锁版本。
 */
@Getter
@Setter
public abstract class StandardVersionedPersistenceFields extends StandardPersistenceFields {

    /**
     * 技术乐观锁版本号。
     */
    @TableField("version")
    @Version
    private Integer version = 0;
}

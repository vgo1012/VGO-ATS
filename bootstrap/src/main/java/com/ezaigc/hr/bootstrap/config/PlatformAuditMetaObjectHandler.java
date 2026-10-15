package com.ezaigc.hr.bootstrap.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.security.CurrentActor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 平台审计元数据自动填充处理器。 Populates the common actor and timestamp fields for MyBatis-Plus entity writes.
 */
@RequiredArgsConstructor
public final class PlatformAuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 时钟。
     */
    private final Clock clock;
    /**
     * 当前操作人提供方。
     */
    private final CurrentActorProvider currentActorProvider;

    /**
     * 新增自动填充。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = clock.instant();
        String actorId = actorId();
        fillIfNull(metaObject, "createId", actorId);
        fillIfNull(metaObject, "createTime", now);
        fillIfNull(metaObject, "updateId", actorId);
        fillIfNull(metaObject, "updateTime", now);
    }

    /**
     * 更新自动填充。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        String actorId = actorId();
        Instant now = clock.instant();
        setIfPresent(metaObject, "updateId", actorId);
        setIfPresent(metaObject, "updateTime", now);
    }

    private String actorId() {
        return currentActorProvider
                .currentActor()
                .map(CurrentActor::getUserId)
                .filter(value -> !value.isBlank())
                .orElse(PlatformActorConstants.SYSTEM_ACTOR_ID);
    }

    /**
     * 仅在目标字段为空时写入自动填充值。
     */
    private void fillIfNull(MetaObject metaObject, String field, Object value) {
        if (metaObject.hasSetter(field) && getFieldValByName(field, metaObject) == null) {
            setFieldValByName(field, value, metaObject);
        }
    }

    /**
     * 仅在实体存在目标字段时更新字段值。
     */
    private void setIfPresent(MetaObject metaObject, String field, Object value) {
        if (metaObject.hasSetter(field)) {
            setFieldValByName(field, value, metaObject);
        }
    }
}

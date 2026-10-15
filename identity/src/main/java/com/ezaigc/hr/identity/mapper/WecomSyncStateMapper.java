package com.ezaigc.hr.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.identity.domain.entity.WecomSyncStateEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WecomSyncStateMapper extends BaseMapper<WecomSyncStateEntity> {
    @SpecialSql("企业同步锁行并发初始化，重复键不覆盖发布状态")
    @Insert("INSERT INTO identity_wecom_sync_state(corp_id) VALUES(#{corpId}) ON DUPLICATE KEY UPDATE corp_id=corp_id")
    void ensure(@Param("corpId") String corpId);

    @SpecialSql("企业级悲观锁串行发布目录和认领批次")
    @Select("""
            SELECT corp_id, active_batch_id, published_batch_id, next_allowed_at,
                   ext, remark, create_id, create_name, create_time, update_id, update_name, update_time, version
            FROM identity_wecom_sync_state WHERE corp_id=#{corpId} FOR UPDATE
            """)
    WecomSyncStateEntity lock(@Param("corpId") String corpId);

    @SpecialSql("租约统一采用数据库时间，避免多实例时钟偏差")
    @Select("SELECT CURRENT_TIMESTAMP(6)")
    Instant databaseNow();
}

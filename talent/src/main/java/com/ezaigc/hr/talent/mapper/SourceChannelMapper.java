package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SourceChannelMapper extends BaseMapper<SourceChannel> {
  @SpecialSql("公司默认来源渠道按稳定编码初始化，INSERT IGNORE 保证多次启动和并发启动幂等")
  @Insert(
      """
      INSERT IGNORE INTO talent_source_channel
          (id,code,name,channel_type,status,version,create_id,create_time,update_id,update_time)
      SELECT #{id},#{code},#{name},#{type},#{status},0,#{actorId},#{now},#{actorId},#{now}
       WHERE NOT EXISTS (
           SELECT 1
             FROM talent_process_event
            WHERE event_type = 'SOURCE_CHANNEL_DELETED'
              AND target_type = 'SOURCE_CHANNEL'
              AND target_id = #{id}
       )
      """)
  int insertIfAbsent(String id, String code, String name, String type, String status,
      String actorId, Instant now);
}

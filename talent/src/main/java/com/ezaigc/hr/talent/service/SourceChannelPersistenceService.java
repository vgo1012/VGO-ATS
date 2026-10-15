package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import java.time.Instant;
import java.util.List;

/** SourceChannel 实体的持久化服务。 */
public interface SourceChannelPersistenceService extends IService<SourceChannel> {

  /** 查询通知或沟通渠道。 */
  SourceChannel findChannel(String id);

  /** 查询渠道操作人编码。 */
  SourceChannel findChannelByCode(String code);

  /** 查询渠道列表。 */
  List<SourceChannel> findChannels(String status, Instant cursorTime, String cursorId, int limit);

  /** 按稳定编码幂等初始化公司公共渠道。 */
  int insertChannelIfAbsent(String id, String code, String name, String type, String actorId, Instant now);

  /** 新增通知或沟通渠道。 */
  int insertChannel(String id, String code, String name, String type, String actorId, Instant now);

  /** 更新通知或沟通渠道。 */
  int updateChannel(String id, String name, String type, long version, String actorId, Instant now);

  /** 停用通知或沟通渠道。 */
  int disableChannel(String id, long version, String actorId, Instant now);

  /** 按版本删除尚未被事实引用的渠道。 */
  int deleteChannel(String id, long version);
}

package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.SourceChannel;
import com.ezaigc.hr.talent.enums.SourceChannelStatus;
import com.ezaigc.hr.talent.mapper.SourceChannelMapper;
import com.ezaigc.hr.talent.service.SourceChannelPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** SourceChannel 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class SourceChannelPersistenceServiceImpl
    extends ServiceImpl<SourceChannelMapper, SourceChannel>
    implements SourceChannelPersistenceService {

  /** 查询通知或沟通渠道。 */
  @Override
  public SourceChannel findChannel(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询渠道操作人编码。 */
  @Override
  public SourceChannel findChannelByCode(String code) {
    return baseMapper.selectOne(
        Wrappers.lambdaQuery(SourceChannel.class).eq(SourceChannel::getCode, code));
  }

  /** 查询渠道列表。 */
  @Override
  public List<SourceChannel> findChannels(
      String status, Instant cursorTime, String cursorId, int limit) {
    var query =
        Wrappers.lambdaQuery(SourceChannel.class)
            .eq(status != null, SourceChannel::getStatus, status)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(SourceChannel::getUpdateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(SourceChannel::getUpdateTime, cursorTime)
                                    .lt(SourceChannel::getId, cursorId)))
            .orderByDesc(SourceChannel::getUpdateTime, SourceChannel::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  @Override
  public int insertChannelIfAbsent(
      String id, String code, String name, String type, String actorId, Instant now) {
    return baseMapper.insertIfAbsent(id, code, name, type, SourceChannelStatus.ACTIVE.getCode(), actorId, now);
  }

  /** 新增通知或沟通渠道。 */
  @Override
  public int insertChannel(
      String id, String code, String name, String type, String actorId, Instant now) {
    SourceChannel channel = new SourceChannel();
    channel.setId(id);
    channel.setCode(code);
    channel.setName(name);
    channel.setChannelType(type);
    channel.setStatus(SourceChannelStatus.ACTIVE.getCode());
    channel.setCreateId(actorId);
    channel.setCreateTime(now);
    channel.setUpdateId(actorId);
    channel.setUpdateTime(now);
    return baseMapper.insert(channel);
  }

  /** 更新通知或沟通渠道。 */
  @Override
  public int updateChannel(
      String id, String name, String type, long version, String actorId, Instant now) {
    SourceChannel channel = new SourceChannel();
    channel.setId(id);
    channel.setName(name);
    channel.setChannelType(type);
    channel.setVersion(version);
    channel.setUpdateId(actorId);
    channel.setUpdateTime(now);
    return baseMapper.updateById(channel);
  }

  /** 停用通知或沟通渠道。 */
  @Override
  public int disableChannel(String id, long version, String actorId, Instant now) {
    SourceChannel channel = new SourceChannel();
    channel.setVersion(version);
    channel.setStatus(SourceChannelStatus.DISABLED.getCode());
    channel.setUpdateId(actorId);
    channel.setUpdateTime(now);
    return baseMapper.update(
        channel,
        Wrappers.lambdaUpdate(SourceChannel.class)
            .eq(SourceChannel::getId, id)
            .eq(SourceChannel::getStatus, SourceChannelStatus.ACTIVE.getCode()));
  }

  @Override
  public int deleteChannel(String id, long version) {
    return baseMapper.delete(
        Wrappers.lambdaQuery(SourceChannel.class)
            .eq(SourceChannel::getId, id)
            .eq(SourceChannel::getVersion, version));
  }
}

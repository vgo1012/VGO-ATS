package com.ezaigc.hr.recruiting.demand.service.impl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdResumeSourceEntity;
import com.ezaigc.hr.recruiting.demand.mapper.JdResumeSourceMapper;
import com.ezaigc.hr.recruiting.demand.service.JdResumeSourcePersistenceService;
import org.springframework.stereotype.Service;
import java.util.List;
import com.ezaigc.hr.common.web.pagination.StableCursor;

@Service
public class JdResumeSourcePersistenceServiceImpl extends ServiceImpl<JdResumeSourceMapper,JdResumeSourceEntity> implements JdResumeSourcePersistenceService {
  public void insertIfAbsent(JdResumeSourceEntity row) { baseMapper.insertIfAbsent(row); }
  public JdResumeSourceEntity findExisting(String mailSourceId, String contentSha256) {
    return baseMapper.selectOne(Wrappers.lambdaQuery(JdResumeSourceEntity.class).eq(JdResumeSourceEntity::getMailSourceId, mailSourceId).eq(JdResumeSourceEntity::getContentSha256,contentSha256).last("FOR UPDATE"));
  }
  public List<JdResumeSourceEntity> findVisiblePage(String id, StableCursor cursor, int fetchSize, String actorId, boolean scopeAll) { return baseMapper.findPage(id,cursor,fetchSize,actorId,scopeAll); }
  public List<JdResumeSourceEntity> findPage(String id, StableCursor cursor,int fetchSize) { return baseMapper.findPage(id,cursor,fetchSize,null,true); }
  public long countByJdVersion(String id) { return baseMapper.countByJdVersion(id); }
}

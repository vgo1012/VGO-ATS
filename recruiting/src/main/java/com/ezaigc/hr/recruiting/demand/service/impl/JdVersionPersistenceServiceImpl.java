package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdVersionStatus;
import com.ezaigc.hr.recruiting.demand.mapper.JdVersionMapper;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** JdVersionEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class JdVersionPersistenceServiceImpl extends ServiceImpl<JdVersionMapper, JdVersionEntity>
    implements JdVersionPersistenceService {

  @Override
  public JdVersionEntity findReusableVersion(String ownerUserId, String fingerprint) {
    var records = baseMapper.selectPage(new Page<JdVersionEntity>(1, 1, false),
        Wrappers.<JdVersionEntity>lambdaQuery().eq(JdVersionEntity::getContentOwnerUserId, ownerUserId)
            .eq(JdVersionEntity::getContentFingerprint, fingerprint)
            .orderByDesc(JdVersionEntity::getCreateTime, JdVersionEntity::getId)).getRecords();
    return records.isEmpty() ? null : records.getFirst();
  }

  @Override
  public JdVersionEntity findActiveExactVersion(
      String ownerUserId,
      String ownerDepartmentId,
      String recruitmentType,
      String title,
      String rawText,
      String clientId,
      String distinction) {
    return baseMapper.findActiveExactVersion(
        ownerUserId, ownerDepartmentId, recruitmentType, title, rawText, clientId, distinction, false);
  }

  @Override
  public JdVersionEntity findActiveExactVersionForUpdate(
      String ownerUserId,
      String ownerDepartmentId,
      String recruitmentType,
      String title,
      String rawText,
      String clientId,
      String distinction) {
    return baseMapper.findActiveExactVersionForUpdate(
        ownerUserId, ownerDepartmentId, recruitmentType, title, rawText, clientId, distinction, false);
  }

  @Override
  public int insertJdVersion(
      String id,
      String jdId,
      int versionNo,
      String title,
      String rawText,
      String sourceType,
      String workRegionCode,
      String actorId,
      Instant now) {
    JdVersionEntity version = new JdVersionEntity();
    version.setId(id);
    version.setJdId(jdId);
    version.setVersionNo(versionNo);
    version.setTitle(title);
    version.setRawText(rawText);
    version.setStatus(JdVersionStatus.DRAFT.getCode());
    version.setSourceType(sourceType);
    version.setWorkRegionCode(workRegionCode);
    version.setCreateId(actorId);
    version.setCreateTime(now);
    return baseMapper.insert(version);
  }

  @Override
  public JdVersionEntity findJdVersion(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public List<String> findVersionIds(String jdId) {
    return baseMapper.selectList(Wrappers.<JdVersionEntity>lambdaQuery()
        .select(JdVersionEntity::getId).eq(JdVersionEntity::getJdId, jdId))
        .stream().map(JdVersionEntity::getId).toList();
  }

  @Override
  public List<JdVersionEntity> findJdVersions(
      String jdId, Instant cursorTime, String cursorId, int limit) {
    var query =
        Wrappers.lambdaQuery(JdVersionEntity.class)
            .eq(JdVersionEntity::getJdId, jdId)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(JdVersionEntity::getCreateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(JdVersionEntity::getCreateTime, cursorTime)
                                    .lt(JdVersionEntity::getId, cursorId)))
            .orderByDesc(JdVersionEntity::getVersionNo, JdVersionEntity::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }
}

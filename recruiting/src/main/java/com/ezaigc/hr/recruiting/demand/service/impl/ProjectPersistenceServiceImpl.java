package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.ProjectEntity;
import com.ezaigc.hr.recruiting.demand.enums.DemandActivationStatus;
import com.ezaigc.hr.recruiting.demand.mapper.ProjectMapper;
import com.ezaigc.hr.recruiting.demand.service.ProjectPersistenceService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/** ProjectEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ProjectPersistenceServiceImpl extends ServiceImpl<ProjectMapper, ProjectEntity>
    implements ProjectPersistenceService {

  /** 查询项目列表。 */
  @Override
  public List<ProjectEntity> findProjects(
      String clientId, String status, Instant cursorTime, String cursorId, int limit) {
    var query =
        Wrappers.lambdaQuery(ProjectEntity.class)
            .eq(ProjectEntity::getClientId, clientId)
            .eq(status != null, ProjectEntity::getStatus, status)
            .and(
                cursorTime != null,
                cursor ->
                    cursor
                        .lt(ProjectEntity::getUpdateTime, cursorTime)
                        .or(
                            sameTime ->
                                sameTime
                                    .eq(ProjectEntity::getUpdateTime, cursorTime)
                                    .lt(ProjectEntity::getId, cursorId)))
            .orderByDesc(ProjectEntity::getUpdateTime, ProjectEntity::getId);
    return baseMapper.selectPage(new Page<>(1, limit, false), query).getRecords();
  }

  /** 查询项目。 */
  @Override
  public ProjectEntity findProject(String id) {
    return baseMapper.selectById(id);
  }

  /** 统计项目编码。 */
  @Override
  public int countProjectCode(String clientId, String code) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ProjectEntity.class)
                .eq(ProjectEntity::getClientId, clientId)
                .eq(ProjectEntity::getCode, code)));
  }

  /** 新增项目。 */
  @Override
  public int insertProject(
      String id, String clientId, String code, String name, String actorId, Instant now) {
    ProjectEntity project = new ProjectEntity();
    project.setId(id);
    project.setClientId(clientId);
    project.setCode(code);
    project.setName(name);
    project.setStatus(DemandActivationStatus.ACTIVE.getCode());
    project.setVersion(0L);
    project.setCreateId(actorId);
    project.setCreateTime(now);
    project.setUpdateId(actorId);
    project.setUpdateTime(now);
    return baseMapper.insert(project);
  }

  /** 更新项目。 */
  @Override
  public int updateProject(String id, String name, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ProjectEntity.class)
            .eq(ProjectEntity::getId, id)
            .eq(ProjectEntity::getVersion, version)
            .setIncrBy(ProjectEntity::getVersion, 1)
            .eq(ProjectEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(ProjectEntity::getName, name)
            .set(ProjectEntity::getUpdateId, actorId)
            .set(ProjectEntity::getUpdateTime, now));
  }

  /** 停用项目。 */
  @Override
  public int disableProject(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ProjectEntity.class)
            .eq(ProjectEntity::getId, id)
            .eq(ProjectEntity::getVersion, version)
            .setIncrBy(ProjectEntity::getVersion, 1)
            .eq(ProjectEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(ProjectEntity::getStatus, DemandActivationStatus.DISABLED.getCode())
            .set(ProjectEntity::getUpdateId, actorId)
            .set(ProjectEntity::getUpdateTime, now));
  }
}

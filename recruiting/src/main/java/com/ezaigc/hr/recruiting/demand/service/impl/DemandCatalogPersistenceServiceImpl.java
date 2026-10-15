package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import com.ezaigc.hr.recruiting.demand.enums.DemandActivationStatus;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCatalogMapper;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DemandCatalogPersistenceServiceImpl
    extends ServiceImpl<DemandCatalogMapper, ClientEntity>
    implements DemandCatalogPersistenceService {
  /** 查询客户。 */
  @Override
  public ClientEntity findClient(String id) {
    return baseMapper.selectById(id);
  }

  @Override
  public int deleteClient(String id, long version) {
    return baseMapper.delete(Wrappers.lambdaQuery(ClientEntity.class)
        .eq(ClientEntity::getId, id).eq(ClientEntity::getVersion, version));
  }

  /** 统计客户编码。 */
  @Override
  public int countClientCode(String code) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ClientEntity.class).eq(ClientEntity::getCode, code)));
  }

  /** 新增客户。 */
  @Override
  public int insertClient(
      String id,
      String code,
      String name,
      String ownerUserId,
      String ownerDepartmentId,
      String actorId,
      Instant now) {
    ClientEntity client = new ClientEntity();
    client.setId(id);
    client.setCode(code);
    client.setName(name);
    client.setStatus(DemandActivationStatus.ACTIVE.getCode());
    client.setOwnerUserId(ownerUserId);
    client.setOwnerDepartmentId(ownerDepartmentId);
    client.setVersion(0L);
    client.setCreateId(actorId);
    client.setCreateTime(now);
    client.setUpdateId(actorId);
    client.setUpdateTime(now);
    return baseMapper.insert(client);
  }

  /** 更新客户。 */
  @Override
  public int updateClient(String id, String name, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ClientEntity.class)
            .eq(ClientEntity::getId, id)
            .eq(ClientEntity::getVersion, version)
            .setIncrBy(ClientEntity::getVersion, 1)
            .eq(ClientEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(ClientEntity::getName, name)
            .set(ClientEntity::getUpdateId, actorId)
            .set(ClientEntity::getUpdateTime, now));
  }

  /** 停用客户。 */
  @Override
  public int disableClient(String id, long version, String actorId, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ClientEntity.class)
            .eq(ClientEntity::getId, id)
            .eq(ClientEntity::getVersion, version)
            .setIncrBy(ClientEntity::getVersion, 1)
            .eq(ClientEntity::getStatus, DemandActivationStatus.ACTIVE.getCode())
            .set(ClientEntity::getStatus, DemandActivationStatus.DISABLED.getCode())
            .set(ClientEntity::getUpdateId, actorId)
            .set(ClientEntity::getUpdateTime, now));
  }

  @Override
  public List<ClientEntity> findClients(
      String status,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findClients(
        status, actorId, scopeAll, managedUserIds, departmentIds, cursorTime, cursorId, limit);
  }

  @Override
  public List<PositionEntity> findPositions(
      String clientId,
      String projectId,
      String status,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findPositions(
        clientId,
        projectId,
        status,
        actorId,
        scopeAll,
        managedUserIds,
        departmentIds,
        cursorTime,
        cursorId,
        limit);
  }

  @Override
  public List<DemandEntity> findDemands(
      String status,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findDemands(
        status, actorId, scopeAll, managedUserIds, departmentIds, cursorTime, cursorId, limit);
  }

  @Override
  public Integer lockJdVersionNo(String id) {
    return baseMapper.lockJdVersionNo(id);
  }

  @Override
  public List<JdEntity> findJds(
      String demandId,
      String status,
      String actorId,
      String ownerUserId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds,
      Instant cursorTime,
      String cursorId,
      int limit) {
    return baseMapper.findJds(
        demandId,
        status,
        actorId,
        ownerUserId,
        scopeAll,
        managedUserIds,
        departmentIds,
        cursorTime,
        cursorId,
        limit);
  }

  @Override
  public long countClients(
      String status,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds) {
    return baseMapper.countClients(
        status, actorId, scopeAll, managedUserIds, departmentIds);
  }

  @Override
  public long countJds(
      String query,
      String clientId,
      String status,
      String actorId,
      boolean scopeAll,
      Set<String> managedUserIds,
      Set<String> departmentIds) {
    return baseMapper.countJds(
        query, clientId, status, actorId, scopeAll, managedUserIds, departmentIds);
  }

  @Override
  public List<PublicJdSourceDTO> findPublicJdSources(String query,
      String activeStatus, String publishedStatus, Instant cursorTime, String cursorId, int limit) {
    return baseMapper.findPublicJdSources(query, activeStatus, publishedStatus,
        cursorTime, cursorId, limit);
  }

  @Override
  public long countPublicJdSources(String query,
      String activeStatus, String publishedStatus) {
    return baseMapper.countPublicJdSources(query, activeStatus, publishedStatus);
  }

  @Override
  public PublicJdSourceDTO findPublicJdSource(String jdId,
      String activeStatus, String publishedStatus) {
    return baseMapper.findPublicJdSource(jdId, activeStatus, publishedStatus);
  }

  @Override
  public JdEntity findJdForUpdate(String id) {
    return baseMapper.findJdForUpdate(id);
  }

  @Override
  public int countOccupiedHeadcount(String jdId) {
    return baseMapper.countOccupiedHeadcount(jdId);
  }

  @Override
  public int countOccupiedHeadcountForUpdate(String jdId) {
    return baseMapper.findOccupiedApplicationIdsForUpdate(jdId).size();
  }

  @Override
  public JdEntity findJd(String id) {
    return baseMapper.findJd(id);
  }

  @Override
  public int findMaxWorkflowVersionNo(String jdVersionId) {
    return baseMapper.findMaxWorkflowVersionNo(jdVersionId);
  }

  @Override
  public List<String> lockWorkflowTemplates(String jdVersionId) {
    return baseMapper.lockWorkflowTemplates(jdVersionId);
  }

  @Override
  public JdVersionEntity findCurrentJdVersionForDemand(String demandId) {
    return baseMapper.findCurrentJdVersionForDemand(demandId);
  }
}

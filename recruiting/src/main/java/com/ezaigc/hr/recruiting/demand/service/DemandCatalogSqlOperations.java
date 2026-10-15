package com.ezaigc.hr.recruiting.demand.service;

import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.DemandEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.domain.dto.PublicJdSourceDTO;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.PositionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface DemandCatalogSqlOperations {

    /**
     * 查询客户列表。
     */
    List<ClientEntity> findClients(
            String status,
            String actorId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds,
            Instant cursorTime,
            String cursorId,
            int limit);

    /**
     * 查询职位列表。
     */
    List<PositionEntity> findPositions(
            String clientId,
            String projectId,
            String status,
            String actorId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds,
            Instant cursorTime,
            String cursorId,
            int limit);

    /**
     * 查询招聘需求列表。
     */
    List<DemandEntity> findDemands(
            String status,
            String actorId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds,
            Instant cursorTime,
            String cursorId,
            int limit);

    Integer lockJdVersionNo(String id);

    List<JdEntity> findJds(
            String demandId,
            String status,
            String actorId,
            String ownerUserId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds,
            Instant cursorTime,
            String cursorId,
            int limit);

    long countClients(
            String status,
            String actorId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds);

    long countJds(
            String query,
            String clientId,
            String status,
            String actorId,
            boolean scopeAll,
            Set<String> managedUserIds,
            Set<String> departmentIds);

    List<PublicJdSourceDTO> findPublicJdSources(String query,
            String activeStatus, String publishedStatus,
            Instant cursorTime, String cursorId, int limit);

    long countPublicJdSources(String query,
            String activeStatus, String publishedStatus);

    PublicJdSourceDTO findPublicJdSource(String jdId,
            String activeStatus, String publishedStatus);

    JdEntity findJd(String id);

    JdEntity findJdForUpdate(String id);

    int countOccupiedHeadcount(String jdId);

    int countOccupiedHeadcountForUpdate(String jdId);

    /**
     * 查询最大流程版本序号。
     */
    int findMaxWorkflowVersionNo(String jdVersionId);

    List<String> lockWorkflowTemplates(String jdVersionId);

    JdVersionEntity findCurrentJdVersionForDemand(String demandId);
}

package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import java.time.Instant;
import java.util.List;

/**
 * MatchPlanVersion 实体的持久化服务。
 */
public interface MatchPlanVersionPersistenceService extends IService<MatchPlanVersion> {

    /**
     * 查询匹配方案版本。
     */
    MatchPlanVersion findMatchPlanVersion(String id);

    /**
     * 查询匹配方案版本列表。
     */
    List<MatchPlanVersion> findMatchPlanVersions(
            String jdVersionId, Instant cursorTime, String cursorId, int limit);

    /**
     * 查询生效匹配方案版本。
     */
    MatchPlanVersion findActiveMatchPlanVersion(String jdVersionId);

    /**
     * 新增匹配方案版本。
     */
    int insertMatchPlanVersion(
            String id,
            String planId,
            String jdVersionId,
            int versionNo,
            String criterionPlanId,
            boolean skillsEnabled,
            boolean roleEnabled,
            boolean yearsEnabled,
            boolean responsibilityEnabled,
            boolean industryProjectEnabled,
            String actorId,
            Instant now);

    /**
     * 更新匹配方案版本。
     */
    int updateMatchPlanVersion(
            String id,
            boolean skillsEnabled,
            boolean roleEnabled,
            boolean yearsEnabled,
            boolean responsibilityEnabled,
            boolean industryProjectEnabled,
            long version,
            String actorId,
            Instant now);

    /**
     * 激活匹配方案。
     */
    int activateMatchPlan(String id, long version, String actorId, Instant now);

    /**
     * 删除匹配方案版本。
     */
    int deleteMatchPlanVersion(String id, long version);
}

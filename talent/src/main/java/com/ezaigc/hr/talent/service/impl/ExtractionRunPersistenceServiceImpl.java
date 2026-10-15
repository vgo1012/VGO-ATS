package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.talent.domain.entity.ExtractionRun;
import com.ezaigc.hr.talent.enums.TalentProcessingRunStatus;
import com.ezaigc.hr.talent.mapper.ExtractionRunMapper;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ExtractionRun 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ExtractionRunPersistenceServiceImpl
    extends ServiceImpl<ExtractionRunMapper, ExtractionRun>
    implements ExtractionRunPersistenceService {

  @Override
  public int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId) {
    return baseMapper.update(Wrappers.lambdaUpdate(ExtractionRun.class)
        .eq(ExtractionRun::getTaskId, taskId).eq(ExtractionRun::getResumeVersionId, versionId)
        .eq(ExtractionRun::getStatus, TalentProcessingRunStatus.RUNNING.getCode())
        .set(ExtractionRun::getStatus, TalentProcessingRunStatus.FAILED.getCode())
        .set(ExtractionRun::getErrorCode, errorCode).set(ExtractionRun::getFinishedAt, now)
        .set(ExtractionRun::getUpdateId, actorId).set(ExtractionRun::getUpdateTime, now)
        .setIncrBy(ExtractionRun::getVersion, 1));
  }

  /** 新增抽取运行。 */
  @Override
  public int insertExtractionRun(String id, String versionId, String taskId, Instant now) {
    ExtractionRun run = new ExtractionRun();
    run.setId(id);
    run.setResumeVersionId(versionId);
    run.setTaskId(taskId);
    run.setRoute("UNAVAILABLE");
    run.setStatus(TalentProcessingRunStatus.RUNNING.getCode());
    run.setPrintableRatio(BigDecimal.ZERO);
    run.setCoverageRatio(BigDecimal.ZERO);
    run.setCreateTime(now);
    return baseMapper.insert(run);
  }

  /** 完成抽取。 */
  @Override
  public int completeExtraction(
      String id,
      String extractorVersion,
      String route,
      int pageCount,
      int successfulPages,
      int fallbackPages,
      BigDecimal printableRatio,
      BigDecimal coverageRatio,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ExtractionRun.class)
            .eq(ExtractionRun::getId, id)
            .eq(ExtractionRun::getStatus, TalentProcessingRunStatus.RUNNING.getCode())
            .set(ExtractionRun::getExtractorVersion, extractorVersion)
            .set(ExtractionRun::getRoute, route)
            .set(ExtractionRun::getStatus, TalentProcessingRunStatus.SUCCEEDED.getCode())
            .set(ExtractionRun::getPageCount, pageCount)
            .set(ExtractionRun::getSuccessfulPages, successfulPages)
            .set(ExtractionRun::getFallbackPages, fallbackPages)
            .set(ExtractionRun::getPrintableRatio, printableRatio)
            .set(ExtractionRun::getCoverageRatio, coverageRatio)
            .set(ExtractionRun::getFinishedAt, now)
            .set(ExtractionRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(ExtractionRun::getUpdateTime, now));
  }

  /** 标记失败抽取。 */
  @Override
  public int failExtraction(String id, String errorCode, Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ExtractionRun.class)
            .eq(ExtractionRun::getId, id)
            .eq(ExtractionRun::getStatus, TalentProcessingRunStatus.RUNNING.getCode())
            .set(ExtractionRun::getStatus, TalentProcessingRunStatus.FAILED.getCode())
            .set(ExtractionRun::getErrorCode, errorCode)
            .set(ExtractionRun::getFinishedAt, now)
            .set(ExtractionRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(ExtractionRun::getUpdateTime, now));
  }

  /** 查询抽取。 */
  @Override
  public ExtractionRun findExtraction(String id) {
    return baseMapper.selectById(id);
  }

  /** 查询最新成功抽取。 */
  @Override
  public String findLatestSuccessfulExtraction(String versionId) {
    var runs =
        baseMapper.selectPage(new Page<ExtractionRun>(1, 1, false),
            Wrappers.lambdaQuery(ExtractionRun.class)
                .select(ExtractionRun::getId)
                .eq(ExtractionRun::getResumeVersionId, versionId)
                .eq(
                    ExtractionRun::getStatus,
                    TalentProcessingRunStatus.SUCCEEDED.getCode())
                .orderByDesc(ExtractionRun::getCreateTime, ExtractionRun::getId)).getRecords();
    return runs.isEmpty() ? null : runs.getFirst().getId();
  }
}

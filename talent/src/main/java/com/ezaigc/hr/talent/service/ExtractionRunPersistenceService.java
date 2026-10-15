package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ExtractionRun;
import java.math.BigDecimal;
import java.time.Instant;

/** ExtractionRun 实体的持久化服务。 */
public interface ExtractionRunPersistenceService extends IService<ExtractionRun> {

  /** 仅终结精确任务和版本绑定的运行中记录，不改写已完成抽取及其页内容。 */
  int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId);

  /** 新增抽取运行。 */
  int insertExtractionRun(String id, String versionId, String taskId, Instant now);

  /** 完成抽取。 */
  int completeExtraction(
      String id,
      String extractorVersion,
      String route,
      int pageCount,
      int successfulPages,
      int fallbackPages,
      BigDecimal printableRatio,
      BigDecimal coverageRatio,
      Instant now);

  /** 标记失败抽取。 */
  int failExtraction(String id, String errorCode, Instant now);

  /** 查询抽取。 */
  ExtractionRun findExtraction(String id);

  /** 查询最新成功抽取。 */
  String findLatestSuccessfulExtraction(String versionId);
}

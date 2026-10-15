package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPageEntity;
import java.util.List;

/** ResumeExtractionPage 实体的持久化服务。 */
public interface ResumeExtractionPagePersistenceService extends IService<ResumeExtractionPage> {

  /** 批量新增同一次抽取运行的页文本。 */
  boolean insertExtractionPages(List<ResumeExtractionPage> pages);

  /** 查询分页结果。 */
  List<ResumeExtractionPageEntity> findPages(String runId);
}

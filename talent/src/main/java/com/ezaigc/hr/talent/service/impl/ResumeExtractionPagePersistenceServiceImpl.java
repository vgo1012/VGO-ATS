package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EXTRACTION_PAGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_EXTRACTION_PAGE_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.service.support.FactBatchValidation.validateBatch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPageEntity;
import com.ezaigc.hr.talent.mapper.ResumeExtractionPageMapper;
import com.ezaigc.hr.talent.service.ResumeExtractionPagePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

/** ResumeExtractionPage 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ResumeExtractionPagePersistenceServiceImpl
    extends ServiceImpl<ResumeExtractionPageMapper, ResumeExtractionPage>
    implements ResumeExtractionPagePersistenceService {

  @Override
  public boolean insertExtractionPages(List<ResumeExtractionPage> pages) {
    validateBatch(pages, MAX_RESUME_EXTRACTION_PAGES, "resume extraction pages");
    return pages.isEmpty() || saveBatch(pages, RESUME_EXTRACTION_PAGE_WRITE_BATCH_SIZE);
  }

  /** 查询分页结果。 */
  @Override
  public List<ResumeExtractionPageEntity> findPages(String runId) {
    return baseMapper
        .selectList(
            Wrappers.lambdaQuery(ResumeExtractionPage.class)
                .eq(ResumeExtractionPage::getExtractionRunId, runId)
                .orderByAsc(ResumeExtractionPage::getPageNo))
        .stream()
        .map(page -> new ResumeExtractionPageEntity(page.getPageNo(), page.getExtractedText()))
        .toList();
  }
}

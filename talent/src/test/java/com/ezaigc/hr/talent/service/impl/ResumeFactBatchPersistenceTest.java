package com.ezaigc.hr.talent.service.impl;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_FACT_CORRECTION_CHANGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EDUCATIONS;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EXTRACTION_PAGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_PROJECT_EXPERIENCES;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_SIMPLE_FACTS;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_SKILLS;
import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_WORK_EXPERIENCES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_EXTRACTION_PAGE_WRITE_BATCH_SIZE;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_WRITE_BATCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.EducationFact;
import com.ezaigc.hr.talent.domain.entity.FactCorrectionChange;
import com.ezaigc.hr.talent.domain.entity.ProjectFact;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import com.ezaigc.hr.talent.domain.entity.SimpleFact;
import com.ezaigc.hr.talent.domain.entity.SkillFact;
import com.ezaigc.hr.talent.domain.entity.WorkFact;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ResumeFactBatchPersistenceTest {

  @TestFactory
  Stream<DynamicTest> preservesEachBatchBoundaryBeforePersistence() {
    var work = spy(new WorkFactPersistenceServiceImpl());
    var projects = spy(new ProjectFactPersistenceServiceImpl());
    var education = spy(new EducationFactPersistenceServiceImpl());
    var skills = spy(new SkillFactPersistenceServiceImpl());
    var simple = spy(new SimpleFactPersistenceServiceImpl());
    var corrections = spy(new FactCorrectionChangePersistenceServiceImpl());
    var pages = spy(new ResumeExtractionPagePersistenceServiceImpl());
    return Stream.of(
        batchBoundary(work, work::insertWorkFacts, new WorkFact(),
            MAX_RESUME_WORK_EXPERIENCES, "work facts", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(projects, projects::insertProjectFacts, new ProjectFact(),
            MAX_RESUME_PROJECT_EXPERIENCES, "project facts", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(education, education::insertEducationFacts, new EducationFact(),
            MAX_RESUME_EDUCATIONS, "education facts", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(skills, skills::insertSkillFacts, new SkillFact(),
            MAX_RESUME_SKILLS, "skill facts", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(simple, simple::insertSimpleFacts, new SimpleFact(),
            MAX_RESUME_SIMPLE_FACTS, "simple facts", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(corrections, corrections::insertCorrectionChanges, new FactCorrectionChange(),
            MAX_FACT_CORRECTION_CHANGES, "fact correction changes", RESUME_FACT_WRITE_BATCH_SIZE),
        batchBoundary(pages, pages::insertExtractionPages, new ResumeExtractionPage(),
            MAX_RESUME_EXTRACTION_PAGES, "resume extraction pages", RESUME_EXTRACTION_PAGE_WRITE_BATCH_SIZE));
  }

  private static <T> DynamicTest batchBoundary(IService<T> service,
      Function<List<T>, Boolean> insert, T value, int maximum, String label, int batchSize) {
    return DynamicTest.dynamicTest(label, () -> {
      assertThat(insert.apply(List.of())).isTrue();
      assertThatThrownBy(() -> insert.apply(null))
          .isInstanceOf(IllegalArgumentException.class).hasMessage(label + " batch is invalid");
      assertThatThrownBy(() -> insert.apply(Collections.singletonList(null)))
          .isInstanceOf(IllegalArgumentException.class).hasMessage(label + " batch is invalid");
      assertThatThrownBy(() -> insert.apply(Collections.nCopies(maximum + 1, value)))
          .isInstanceOf(IllegalArgumentException.class).hasMessage(label + " batch is invalid");
      verify(service, never()).saveBatch(anyCollection(), anyInt());

      List<T> accepted = Collections.nCopies(maximum, value);
      doReturn(true).when(service).saveBatch(anyCollection(), eq(batchSize));
      assertThat(insert.apply(accepted)).isTrue();
      verify(service).saveBatch(accepted, batchSize);
      doReturn(false).when(service).saveBatch(anyCollection(), eq(batchSize));
      assertThat(insert.apply(List.of(value))).isFalse();
    });
  }
}

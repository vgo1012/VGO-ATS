package com.ezaigc.hr.recruiting.interview.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdVersionEntity;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdVersionPersistenceService;
import com.ezaigc.hr.recruiting.interview.application.manager.InterviewApplicationManager;
import com.ezaigc.hr.recruiting.interview.domain.entity.Interview;
import com.ezaigc.hr.recruiting.interview.domain.entity.InterviewDetails;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InterviewMaterialAccessAdapterTest {
  private final InterviewApplicationManager interviews=mock(InterviewApplicationManager.class);
  private final ApplicationPersistenceService applications=mock(ApplicationPersistenceService.class);
  private final JdVersionPersistenceService versions=mock(JdVersionPersistenceService.class);
  private final JdEntityPersistenceService jds=mock(JdEntityPersistenceService.class);
  private final InterviewMaterialAccessAdapter adapter=new InterviewMaterialAccessAdapter();
  @BeforeEach void setup() {
    ReflectionTestUtils.setField(adapter,"interviews",interviews);
    ReflectionTestUtils.setField(adapter,"applications",applications);
    ReflectionTestUtils.setField(adapter,"jdVersions",versions);
    ReflectionTestUtils.setField(adapter,"jds",jds);
  }
  @Test void deniedInterviewNeverReadsCandidateOrJobMaterials() {
    when(interviews.get("interview")).thenThrow(PlatformExceptions.forbidden("DENIED","未受邀"));
    assertThatThrownBy(()->adapter.requireMaterialAccess("interview")).hasMessageContaining("未受邀");
    verifyNoInteractions(applications,versions);
  }
  @Test void usesApplicationFrozenResumeAndExactInterviewJobVersion() {
    fixture();
    var jd=new JdEntity(); jd.setCode("JD-000001");
    when(jds.getById("jd")).thenReturn(jd);
    var result=adapter.requireMaterialAccess("interview");
    assertThat(result.resumeVersionId()).isEqualTo("frozen-resume");
    assertThat(result.jdCode()).isEqualTo("JD-000001");
    assertThat(result.jdVersionId()).isEqualTo("frozen-jd");
    assertThat(result.jdRawText()).isEqualTo("原始岗位材料");
    verify(versions).findJdVersion("frozen-jd");
  }
  @Test void brokenApplicationBindingCannotExposeAnotherCandidatesMaterial() {
    var application=fixture();
    application.setCandidateId("another-candidate");
    assertThatThrownBy(()->adapter.requireMaterialAccess("interview")).hasMessageContaining("不一致");
  }
  private Application fixture() {
    var interview=new Interview();
    interview.setId("interview"); interview.setApplicationId("application");
    interview.setCandidateId("candidate"); interview.setJdId("jd"); interview.setJdVersionId("frozen-jd");
    when(interviews.get("interview")).thenReturn(new InterviewDetails(interview,List.of()));
    var application=new Application(); application.setCandidateId("candidate");
    application.setResumeVersionId("frozen-resume"); application.setJdVersionId("frozen-jd");
    when(applications.findApplication("application")).thenReturn(application);
    var jd=new JdVersionEntity(); jd.setId("frozen-jd"); jd.setJdId("jd");
    jd.setVersionNo(1); jd.setTitle("运维工程师"); jd.setRawText("原始岗位材料");
    when(versions.findJdVersion("frozen-jd")).thenReturn(jd);
    return application;
  }
}

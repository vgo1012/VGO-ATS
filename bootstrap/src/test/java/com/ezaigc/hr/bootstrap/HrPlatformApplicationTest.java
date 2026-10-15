package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.core.module.ModuleRegistry;
import com.ezaigc.hr.bootstrap.config.MybatisPlatformConfiguration;
import com.ezaigc.hr.recruiting.activity.api.ActivityOriginRecorder;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityOriginManager;
import com.ezaigc.hr.recruiting.activity.application.manager.ActivityRecordManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@NoDatabasePlatformTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class HrPlatformApplicationTest {

  private final ModuleRegistry moduleRegistry;
  private final MockMvc mockMvc;
  private final ActivityOriginRecorder activityOrigins;
  private final ActivityRecordManager activityRecords;

  @Test
  void registersRealActivityComponentsAndProductionMapperScan() {
    assertThat(AopUtils.getTargetClass(activityOrigins)).isEqualTo(ActivityOriginManager.class);
    assertThat(AopUtils.getTargetClass(activityRecords)).isEqualTo(ActivityRecordManager.class);
    assertThat(MybatisPlatformConfiguration.class.getAnnotation(MapperScan.class).basePackages())
        .contains("com.ezaigc.hr.recruiting.activity");
  }

  @Test
  void loadsAllBusinessModules() {
    assertThat(moduleRegistry.moduleNames())
        .containsExactly(
            "identity",
            "integration",
            "intelligence",
            "recruiting",
            "reporting",
            "talent");
  }

  @Test
  void publishesVersionedOpenApiAndHealthEndpoint() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("VGO ATS API"))
        .andExpect(jsonPath("$.paths['/api/v1/candidate-batch'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/resume-version-fact-summaries'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/work-calendars/{year}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/work-calendars/{year}/published'].get").exists())
        .andExpect(jsonPath("$.components.schemas.PublishedWorkCalendarResponse.properties.length()").value(3))
        .andExpect(jsonPath("$.paths['/api/v1/admin/work-calendars/{year}'].put").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/work-calendars/{year}/drafts']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/admin/work-calendars/{year}/drafts/{id}/publish']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/admin/resume-lifecycle/runs'].get").exists())
        .andExpect(jsonPath("$.components.schemas.CandidatePoolStatusResponse.properties.lifecycle").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeLifecycleResponse.properties.version.type").value("string"))
        .andExpect(jsonPath("$.paths['/api/v1/resume-version-sources'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/interview-feedback-batch'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/resume-version-references'].get").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeVersionReferenceResponse.properties.length()").value(3))
        .andExpect(jsonPath("$.components.schemas.TalentSearchPageResponse.properties.scanIncomplete.type").value("boolean"))
        .andExpect(jsonPath("$.components.schemas.MetricReportResponse.properties.projectionStatus").exists())
        .andExpect(jsonPath("$.components.schemas.MetricReportResponse.properties.reconciliationUpdatedAt").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.jdTitle").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.jdCode").exists())
        .andExpect(jsonPath("$.components.schemas.JdVersionResponse.properties.jdCode").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jd-code-options'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jd-code-options'].post").exists())
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.jobTypeCode").exists())
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.workRegionCode").exists())
        .andExpect(jsonPath("$.components.schemas.CandidateJobContextResponse.properties.jdCode").exists())
        .andExpect(jsonPath("$.components.schemas.InterviewMaterialResponse.properties.jdCode").exists())
        .andExpect(jsonPath("$.components.schemas.RecruitmentReadinessResponse.properties.jdCode").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.importStatus").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.processingStatus").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.duplicateContent").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.scoreStatus").exists())
        .andExpect(jsonPath("$.components.schemas.ResumeIntakeResponse.properties.scoreRunId").exists())
        .andExpect(jsonPath("$.paths['/api/v1/intakes/{id}/import'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/intakes/count'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/mail-resume-reviews'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/mail-resume-reviews/{id}/jd'].put").exists())
        .andExpect(jsonPath("$.paths['/api/v1/mail-resume-jds'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/mail-resume-ingest-runs/{id}'].get").exists())
        .andExpect(jsonPath("$.components.schemas.TalentIntakeImportRequest.properties.version.type").value("string"))
        .andExpect(jsonPath("$.paths['/api/v1/applications/{id}/next-actions'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/applications/{applicationId}/activity-records'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/follow-up-plans'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/applications/{id}/follow-up-plans'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/applications/{id}/follow-up-plans/{planId}/cancel'].post").exists())
        .andExpect(jsonPath("$.components.schemas.FollowUpPlanRequest.properties.version.type").value("string"))
        .andExpect(jsonPath("$.components.schemas.FollowUpPlanResponse.properties.resultReason").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/public-sources'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/public-sources/count'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/public-sources/{jdId}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/department-sources']").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.contentShared.type").value("boolean"))
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.appliedFromJdVersionId").exists())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.rawText").exists())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.ownerUserId").exists())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.ownerUserName").exists())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.ownerDepartmentId").exists())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.ownerDepartmentName").exists())
        .andExpect(jsonPath("$.components.schemas.AuthMeResponse.properties.supportDepartmentIds.type").value("array"))
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.clientId").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.headcount").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.recruitmentType").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.PublicJdSourceResponse.properties.recruitmentCycleDays").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.properties.contentShared").exists())
        .andExpect(jsonPath("$.components.schemas.UpdateJdVersionRequest.properties.contentShared").exists())
        .andExpect(jsonPath("$.paths['/api/v1/directory/departments'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/{jdId}/versions'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jds/{jdId}'].delete").exists())
        .andExpect(jsonPath("$.paths['/api/v1/jd-versions/{jdVersionId}'].get.responses['200'].content['*/*'].schema['$ref']").value("#/components/schemas/JdVersionResponse"))
        .andExpect(jsonPath("$.paths['/api/v1/jd-versions/{jdVersionId}/sources'].get").exists())
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.properties.code").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.properties.ownerDepartmentId").exists())
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.properties.recruitmentType.pattern").value("INTERNAL|EXTERNAL"))
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.required").value(hasItems("ownerDepartmentId", "recruitmentType", "workRegionName")))
        .andExpect(jsonPath("$.components.schemas.UpdateJdVersionRequest.required").value(not(hasItem("ownerDepartmentId"))))
        .andExpect(jsonPath("$.components.schemas.CreateJdVersionRequest.required").value(not(hasItem("distinction"))))
        .andExpect(jsonPath("$.components.schemas.UpdateJdVersionRequest.required").value(not(hasItem("distinction"))))
        .andExpect(jsonPath("$.components.schemas.UpdateJdVersionRequest.properties.workRegionName").exists())
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.workRegionName").exists())
        .andExpect(jsonPath("$.components.schemas.JdResponse.properties.onboardedHeadcount").exists())
        .andExpect(jsonPath("$.paths['/api/v1/exports']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/me/overdue-thresholds']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/workbench/thresholds']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/items']").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/v1/tasks'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/cancel'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/retry-failed'].post").exists());

    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}

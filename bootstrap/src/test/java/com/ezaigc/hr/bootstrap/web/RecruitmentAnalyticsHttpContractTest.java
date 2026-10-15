package com.ezaigc.hr.bootstrap.web;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.reporting.application.manager.RecruitmentAnalyticsManager;
import com.ezaigc.hr.reporting.controller.RecruitmentAnalyticsController;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Channel;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Demand;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Department;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Efficiency;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.HrWorkload;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Milestone;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Portrait;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Risk;
import com.ezaigc.hr.reporting.domain.response.RecruitmentAnalyticsResponse.Trend;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 真实 MVC 转换器、输入校验与 ProblemDetail 映射的 HTTP 契约。 */
class RecruitmentAnalyticsHttpContractTest {
  private static final String URL="/api/v1/reports/recruitment-analysis";
  private static final String ID="01J"+"A".repeat(23);
  private static final Instant FROM=Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant TO=Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant NOW=Instant.parse("2026-09-15T00:00:00Z");
  private final RecruitmentAnalyticsManager manager=mock(RecruitmentAnalyticsManager.class);
  private MockMvc mvc;

  @BeforeEach void setUp() {
    var controller=new RecruitmentAnalyticsController();
    ReflectionTestUtils.setField(controller,"manager",manager);
    mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @Test void serializesEveryNestedSliceAndForwardsFiltersWithoutChangingThem() throws Exception {
    var response=new RecruitmentAnalyticsResponse(FROM,TO,NOW,new Demand(2,5,1,1,4),new Risk(3),
        List.of(new Milestone("RESUME","全部简历",5),new Milestone("APPLICATION","进入流程",3)),
        List.of(new Trend("2026-09-02",3,1,0)),
        List.of(new Channel("UNKNOWN","未知渠道",5,3,1,0)),
        List.of(new Portrait("AGE","未知",5,1,1,1,0)),
        new Efficiency(0,null,3,12.5),List.of(new Department(ID,"研发部")),
        List.of(new HrWorkload(ID,"招聘同事",3,2,1,0,1,5,2,1,0,true,false)),List.of("部门按绑定 JD 统计"));
    when(manager.report(FROM,TO,ID,ID,"INTERNAL")).thenReturn(response);
    mvc.perform(get(URL).param("from",FROM.toString()).param("to",TO.toString())
        .param("departmentId",ID).param("jdId",ID).param("recruitmentType","INTERNAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value(FROM.toString()))
        .andExpect(jsonPath("$.to").value(TO.toString()))
        .andExpect(jsonPath("$.asOf").value(NOW.toString()))
        .andExpect(jsonPath("$.demand.activeJobs").value(2))
        .andExpect(jsonPath("$.demand.headcount").value(5))
        .andExpect(jsonPath("$.demand.unknownHeadcountJobs").value(1))
        .andExpect(jsonPath("$.demand.occupied").value(1))
        .andExpect(jsonPath("$.demand.openHeadcount").value(4))
        .andExpect(jsonPath("$.risk.activeBlacklists").value(3))
        .andExpect(jsonPath("$.funnel[0].key").value("RESUME"))
        .andExpect(jsonPath("$.funnel[0].label").value("全部简历"))
        .andExpect(jsonPath("$.funnel[0].count").value(5))
        .andExpect(jsonPath("$.funnel[1].key").value("APPLICATION"))
        .andExpect(jsonPath("$.funnel[1].label").value("进入流程"))
        .andExpect(jsonPath("$.funnel[1].count").value(3))
        .andExpect(jsonPath("$.trend[0].date").value("2026-09-02"))
        .andExpect(jsonPath("$.trend[0].applications").value(3))
        .andExpect(jsonPath("$.trend[0].offers").value(1))
        .andExpect(jsonPath("$.trend[0].onboarded").value(0))
        .andExpect(jsonPath("$.channels[0].id").value("UNKNOWN"))
        .andExpect(jsonPath("$.channels[0].name").value("未知渠道"))
        .andExpect(jsonPath("$.channels[0].resumes").value(5))
        .andExpect(jsonPath("$.channels[0].applications").value(3))
        .andExpect(jsonPath("$.channels[0].offers").value(1))
        .andExpect(jsonPath("$.channels[0].onboarded").value(0))
        .andExpect(jsonPath("$.portraits[0].dimension").value("AGE"))
        .andExpect(jsonPath("$.portraits[0].label").value("未知"))
        .andExpect(jsonPath("$.portraits[0].resumes").value(5))
        .andExpect(jsonPath("$.portraits[0].passed").value(1))
        .andExpect(jsonPath("$.portraits[0].rejected").value(1))
        .andExpect(jsonPath("$.portraits[0].pending").value(1))
        .andExpect(jsonPath("$.portraits[0].withdrawn").value(0))
        .andExpect(jsonPath("$.efficiency.completedCount").value(0))
        .andExpect(jsonPath("$.efficiency.averageTotalHours").isEmpty())
        .andExpect(jsonPath("$.efficiency.activeCount").value(3))
        .andExpect(jsonPath("$.efficiency.averageActiveHours").value(12.5))
        .andExpect(jsonPath("$.departments[0].id").value(ID))
        .andExpect(jsonPath("$.departments[0].name").value("研发部"))
        .andExpect(jsonPath("$.hrWorkload[0].ownerUserId").value(ID))
        .andExpect(jsonPath("$.hrWorkload[0].displayName").value("招聘同事"))
        .andExpect(jsonPath("$.hrWorkload[0].applications").value(3))
        .andExpect(jsonPath("$.hrWorkload[0].interviews").value(2))
        .andExpect(jsonPath("$.hrWorkload[0].offers").value(1))
        .andExpect(jsonPath("$.hrWorkload[0].onboarded").value(0))
        .andExpect(jsonPath("$.hrWorkload[0].rejected").value(1))
        .andExpect(jsonPath("$.hrWorkload[0].resumes").value(5))
        .andExpect(jsonPath("$.hrWorkload[0].interview1").value(2))
        .andExpect(jsonPath("$.hrWorkload[0].interview2").value(1))
        .andExpect(jsonPath("$.hrWorkload[0].interview3Plus").value(0))
        .andExpect(jsonPath("$.hrWorkload[0].self").value(true))
        .andExpect(jsonPath("$.hrWorkload[0].inactive").value(false))
        .andExpect(jsonPath("$.notes[0]").value("部门按绑定 JD 统计"));
    verify(manager).report(FROM,TO,ID,ID,"INTERNAL");
  }

  @Test void hiddenWorkloadIsNullRatherThanAnEmptyVisibleSection() throws Exception {
    var response=new RecruitmentAnalyticsResponse(FROM,TO,NOW,new Demand(0,0,0,0,0),new Risk(0),
        List.of(),List.of(),List.of(),List.of(),new Efficiency(0,null,0,null),List.of(),null,List.of());
    when(manager.report(FROM,TO,null,null,null)).thenReturn(response);
    mvc.perform(get(URL).param("from",FROM.toString()).param("to",TO.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(hasKey("hrWorkload")))
        .andExpect(jsonPath("$.hrWorkload").isEmpty());
  }

  @Test void absentDatesReachTheManagerAsACumulativeQuery() throws Exception {
    mvc.perform(get(URL).param("departmentId",ID).param("jdId",ID).param("recruitmentType","EXTERNAL"))
        .andExpect(status().isOk());
    verify(manager).report(null,null,ID,ID,"EXTERNAL");
  }

  @Test void incompleteTimeRangeRetainsTheStableValidationError() throws Exception {
    var failure=new PlatformException(HttpStatus.BAD_REQUEST,"REPORT_TIME_RANGE_INVALID","统计时间必须同时提供",true);
    when(manager.report(FROM,null,null,null,null)).thenThrow(failure);
    when(manager.report(null,TO,null,null,null)).thenThrow(failure);
    mvc.perform(get(URL).param("from",FROM.toString())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REPORT_TIME_RANGE_INVALID"));
    mvc.perform(get(URL).param("to",TO.toString())).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REPORT_TIME_RANGE_INVALID"));
    verify(manager).report(FROM,null,null,null,null);
    verify(manager).report(null,TO,null,null,null);
  }

  @Test void malformedTimeIsRejectedBeforeTheManager() throws Exception {
    mvc.perform(get(URL).param("from","not-a-time").param("to",TO.toString())).andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }

  @Test void malformedDepartmentAndJdAreRejectedBeforeTheManager() throws Exception {
    for (String filter:List.of("departmentId","jdId")) {
      mvc.perform(get(URL).param("from",FROM.toString()).param("to",TO.toString()).param(filter,"invalid"))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(manager);
  }

  @Test void deniedPermissionUses403ProblemDetail() throws Exception {
    when(manager.report(FROM,TO,null,null,null)).thenThrow(new PlatformException(HttpStatus.FORBIDDEN,
        "REPORT_SCOPE_FORBIDDEN","目标数据不在可查看范围",false));
    mvc.perform(get(URL).param("from",FROM.toString()).param("to",TO.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("REPORT_SCOPE_FORBIDDEN"))
        .andExpect(jsonPath("$.detail").value("目标数据不在可查看范围"));
  }

  @Test void invalidRecruitmentTypeIsRejectedBeforeTheManager() throws Exception {
    mvc.perform(get(URL).param("from",FROM.toString()).param("to",TO.toString()).param("recruitmentType","ALL"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(manager);
  }
}

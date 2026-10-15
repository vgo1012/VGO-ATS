package com.ezaigc.hr.recruiting.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.recruiting.demand.api.DemandQueryService;
import com.ezaigc.hr.recruiting.demand.api.JdVersionSnapshot;
import com.ezaigc.hr.recruiting.recruitment.application.manager.ApplicationBatchApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.application.manager.RecruitmentApplicationManager;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.CreateApplicationDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.dto.ApplicationBatchCreateDTO;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Application;
import com.ezaigc.hr.recruiting.recruitment.service.ApplicationPersistenceService;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationDetailsResponse;
import com.ezaigc.hr.recruiting.recruitment.domain.response.ApplicationResponse;
import com.ezaigc.hr.recruiting.recruitment.enums.ApplicationBatchItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationBatchApplicationManagerTest {
  @Test
  void reportsAllHundredItemsAndContinuesAfterBlacklistDuplicateAndUnexpectedFailure() {
    var delegate = mock(RecruitmentApplicationManager.class);
    var manager = new ApplicationBatchApplicationManager();
    ReflectionTestUtils.setField(manager, "applications", delegate);
    List<String> visited = new ArrayList<>();
    when(delegate.createApplicationWithPath(any(), any(), anyString())).thenAnswer(invocation -> {
      CreateApplicationDTO item = invocation.getArgument(0);
      visited.add(item.getCandidateId());
      if ("candidate-20".equals(item.getCandidateId()))
        throw PlatformExceptions.forbidden("COMPANY_BLACKLIST_ACTIVE", "公司黑名单限制");
      if ("candidate-40".equals(item.getCandidateId()))
        throw PlatformExceptions.conflict("ACTIVE_APPLICATION_EXISTS", "已存在有效活动");
      if ("candidate-60".equals(item.getCandidateId())) throw new IllegalStateException("temporary failure");
      return details("app-" + item.getCandidateId());
    });
    var demands = mock(DemandQueryService.class);
    var persistence = mock(ApplicationPersistenceService.class);
    ReflectionTestUtils.setField(manager, "demands", demands);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    var jd = mock(JdVersionSnapshot.class); when(jd.getJdId()).thenReturn("jd-id");
    when(demands.requireJdVersion("jd")).thenReturn(jd);
    var existing = new Application(); existing.setId("existing-app");
    when(persistence.findActiveForCandidateAndJd("candidate-40", "jd-id")).thenReturn(existing);
    when(delegate.canProcessJdVersion("jd")).thenReturn(true);
    var existingResponse = details("existing-app");
    when(delegate.applicationResponse("existing-app")).thenReturn(existingResponse);
    var result = manager.create(new ApplicationBatchCreateDTO(IntStream.range(0, 100).mapToObj(i -> command("candidate-" + i)).toList(), List.of()), "batch-key");
    assertThat(visited).hasSize(100);
    assertThat(result.getItems()).hasSize(100);
    assertThat(result.getItems()).filteredOn(item -> item.getStatus() == ApplicationBatchItemStatus.SUCCEEDED).hasSize(97);
    assertThat(result.getItems().get(20).getErrorCode()).isEqualTo("COMPANY_BLACKLIST_ACTIVE");
    assertThat(result.getItems().get(40).getStatus()).isEqualTo(ApplicationBatchItemStatus.ALREADY_EXISTS);
    assertThat(result.getItems().get(40).getApplicationId()).isEqualTo("existing-app");
    assertThat(result.getItems().get(60).getMessage()).doesNotContain("temporary failure");
    assertThat(result.getItems().get(99).getStatus()).isEqualTo(ApplicationBatchItemStatus.SUCCEEDED);
  }

  @Test
  void timeoutOrSubsetRetryReusesTheSameCandidateKey() {
    var delegate = mock(RecruitmentApplicationManager.class);
    var manager = new ApplicationBatchApplicationManager();
    ReflectionTestUtils.setField(manager, "applications", delegate);
    List<String> keys = new ArrayList<>();
    when(delegate.createApplicationWithPath(any(), any(), anyString())).thenAnswer(invocation -> {
      keys.add(invocation.getArgument(2));
      return details("app");
    });
    manager.create(new ApplicationBatchCreateDTO(List.of(command("one"), command("two")), null), "original-request");
    manager.create(new ApplicationBatchCreateDTO(List.of(command("two")), null), "original-request");
    assertThat(keys.get(1)).isEqualTo(keys.get(2)).isNotEqualTo(keys.get(0));
  }

  private static CreateApplicationDTO command(String candidateId) {
    return new CreateApplicationDTO(candidateId, "jd", "resume-" + candidateId, null, "人工选择推荐");
  }

  private static ApplicationDetailsResponse details(String id) {
    var response = mock(ApplicationDetailsResponse.class);
    var application = mock(ApplicationResponse.class);
    when(response.getApplication()).thenReturn(application);
    when(application.getId()).thenReturn(id);
    return response;
  }
}

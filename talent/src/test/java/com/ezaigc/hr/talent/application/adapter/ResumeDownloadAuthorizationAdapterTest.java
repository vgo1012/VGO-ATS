package com.ezaigc.hr.talent.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.talent.application.manager.TalentIntakeApplicationManager;
import com.ezaigc.hr.talent.domain.response.DownloadGrantResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeDownloadAuthorizationAdapterTest {
  @Test
  void delegatesToTheExistingAuditedAuthorizationUseCase() {
    var manager = mock(TalentIntakeApplicationManager.class);
    when(manager.authorizeDownload("version-id"))
        .thenReturn(new DownloadGrantResponse("single-use-token", Instant.MAX));
    var adapter = new ResumeDownloadAuthorizationAdapter();
    ReflectionTestUtils.setField(adapter, "manager", manager);
    assertThat(adapter.authorize("version-id")).isEqualTo("single-use-token");
    verify(manager).authorizeDownload("version-id");
  }
}

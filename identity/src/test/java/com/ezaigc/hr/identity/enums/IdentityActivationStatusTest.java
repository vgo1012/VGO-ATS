package com.ezaigc.hr.identity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.identity.domain.entity.DepartmentRecord;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import org.junit.jupiter.api.Test;

class IdentityActivationStatusTest {

  @Test
  void accountAndDepartmentUseTheSameTypedActivationVocabulary() {
    UserAccountRecord account = new UserAccountRecord();
    DepartmentRecord department = new DepartmentRecord();

    account.setStatus(IdentityActivationStatus.ACTIVE.getCode());
    department.setStatus(IdentityActivationStatus.ACTIVE.getCode());
    assertThat(account.active()).isTrue();
    assertThat(department.active()).isTrue();

    account.setStatus(IdentityActivationStatus.DISABLED.getCode());
    department.setStatus(null);
    assertThat(account.active()).isFalse();
    assertThat(department.active()).isFalse();
  }

  @Test
  void handoverStatusesRoundTripPersistedCodes() {
    assertThat(HandoverBatchStatus.fromCode("RUNNING")).isEqualTo(HandoverBatchStatus.RUNNING);
    assertThat(HandoverBatchStatus.fromCode("PARTIAL_SUCCESS"))
        .isEqualTo(HandoverBatchStatus.PARTIAL_SUCCESS);
    assertThat(HandoverItemStatus.fromCode("UNRESOLVED"))
        .isEqualTo(HandoverItemStatus.UNRESOLVED);
  }
}

package com.ezaigc.hr.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentActorOperationalScopeTest {

  @Test
  void removesManagementChainWithoutRemovingCompanyOrIdentityContext() {
    var actor = new CurrentActorSnapshot(
        "manager", "招聘负责人", "manager@example.invalid", "department",
        Set.of("support"), Set.of("member"), Set.of(), Set.of("RECRUITMENT_MANAGER"),
        Set.of("talent.read"), false, false);

    var operational = actor.operationalScope();

    assertThat(actor.getManagedUserIds()).containsExactly("member");
    assertThat(actor.getOperationalManagedUserIds()).isEmpty();
    assertThat(operational.getManagedUserIds()).isEmpty();
    assertThat(operational.getUserId()).isEqualTo("manager");
    assertThat(operational.getPermissions()).containsExactly("talent.read");
    assertThat(operational.hasCompanyWideScope()).isFalse();
  }

  @Test
  void keepsHumanResourcesDirectorCompanyScope() {
    var actor = new CurrentActorSnapshot(
        "director", "人力负责人", "director@example.invalid", "department",
        Set.of(), Set.of("member"), Set.of(), Set.of("HR_DIRECTOR"),
        Set.of("talent.read"), true, false);

    assertThat(actor.operationalScope().hasCompanyWideScope()).isTrue();
    assertThat(actor.getOperationalManagedUserIds()).isEmpty();
  }
}

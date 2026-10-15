package com.ezaigc.hr.common.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentActorTest {

    @Test
    void superAdminRetainsAllActionsWithoutBypassingBusinessStateRules() {
        CurrentActor superAdmin = new CurrentActor("01JADMIN", Set.of(), true);
        CurrentActor recruiter = new CurrentActor("01JHR", Set.of("talent.library.view"), false);

        assertThat(superAdmin.hasPermission("any.future.permission")).isTrue();
        assertThat(recruiter.hasPermission("talent.library.view")).isTrue();
        assertThat(recruiter.hasPermission("admin.scoring.activate")).isFalse();
    }
}

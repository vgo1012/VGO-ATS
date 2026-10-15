package com.ezaigc.hr.identity.service.impl;

import static com.ezaigc.hr.identity.constant.IdentityConstants.MAX_SUPPORT_DEPARTMENTS_PER_USER;
import static com.ezaigc.hr.identity.constant.IdentityConstants.USER_ORGANIZATION_WRITE_BATCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.ezaigc.hr.identity.domain.entity.Membership;
import com.ezaigc.hr.identity.domain.entity.UserSupportDepartment;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IdentityOrganizationAssignmentBatchValidationTest {

  private static final String USER_ID = "U".repeat(26);
  private static final String HOME_DEPARTMENT_ID = "H".repeat(26);
  private static final String ACTOR_ID = "A".repeat(26);
  private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

  @Test
  void emptySupportDepartmentBatchDoesNotOpenADataSession() {
    assertThat(
            new UserSupportDepartmentPersistenceServiceImpl()
                .insertSupportDepartments(USER_ID, Set.of(), ACTOR_ID, NOW))
        .isTrue();
  }

  @Test
  void validAssignmentsAreMappedIntoTwoBoundedJdbcBatches() {
    MembershipPersistenceServiceImpl memberships = spy(new MembershipPersistenceServiceImpl());
    UserSupportDepartmentPersistenceServiceImpl supportDepartments =
        spy(new UserSupportDepartmentPersistenceServiceImpl());
    Set<String> supportDepartmentIds = Set.of("S".repeat(26), "T".repeat(26));
    doReturn(true)
        .when(memberships)
        .saveBatch(anyCollection(), eq(USER_ORGANIZATION_WRITE_BATCH_SIZE));
    doReturn(true)
        .when(supportDepartments)
        .saveBatch(anyCollection(), eq(USER_ORGANIZATION_WRITE_BATCH_SIZE));

    assertThat(
            memberships.insertOrganizationMemberships(
                USER_ID, HOME_DEPARTMENT_ID, supportDepartmentIds, ACTOR_ID, NOW))
        .isTrue();
    assertThat(
            supportDepartments.insertSupportDepartments(
                USER_ID, supportDepartmentIds, ACTOR_ID, NOW))
        .isTrue();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Membership>> membershipBatch =
        ArgumentCaptor.forClass(Collection.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<UserSupportDepartment>> supportBatch =
        ArgumentCaptor.forClass(Collection.class);
    verify(memberships)
        .saveBatch(membershipBatch.capture(), eq(USER_ORGANIZATION_WRITE_BATCH_SIZE));
    verify(supportDepartments)
        .saveBatch(supportBatch.capture(), eq(USER_ORGANIZATION_WRITE_BATCH_SIZE));
    assertThat(membershipBatch.getValue())
        .extracting(Membership::getDepartmentId, Membership::getMembershipType)
        .containsExactlyInAnyOrder(
            tuple(HOME_DEPARTMENT_ID, "HOME"),
            tuple("S".repeat(26), "SUPPORT"),
            tuple("T".repeat(26), "SUPPORT"));
    assertThat(supportBatch.getValue())
        .extracting(UserSupportDepartment::getDepartmentId)
        .containsExactlyInAnyOrder("S".repeat(26), "T".repeat(26));
  }

  @Test
  void relationshipBatchesRejectNullElementsAndOversizedCollectionsBeforePersistence() {
    MembershipPersistenceServiceImpl memberships = new MembershipPersistenceServiceImpl();
    UserSupportDepartmentPersistenceServiceImpl supportDepartments =
        new UserSupportDepartmentPersistenceServiceImpl();
    Set<String> oversized =
        IntStream.rangeClosed(0, MAX_SUPPORT_DEPARTMENTS_PER_USER)
            .mapToObj(Integer::toString)
            .collect(Collectors.toSet());

    assertThatThrownBy(
            () ->
                memberships.insertOrganizationMemberships(
                    USER_ID, HOME_DEPARTMENT_ID, null, ACTOR_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identity organization membership batch is invalid");
    assertThatThrownBy(
            () ->
                memberships.insertOrganizationMemberships(
                    USER_ID,
                    HOME_DEPARTMENT_ID,
                    Collections.singleton(null),
                    ACTOR_ID,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identity organization membership batch is invalid");
    assertThatThrownBy(
            () ->
                supportDepartments.insertSupportDepartments(
                    USER_ID, oversized, ACTOR_ID, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("identity support department batch is invalid");
  }
}

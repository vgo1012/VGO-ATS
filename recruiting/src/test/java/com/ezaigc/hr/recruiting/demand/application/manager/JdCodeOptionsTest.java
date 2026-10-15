package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.AuthorizationService;
import com.ezaigc.hr.identity.api.CurrentActorSnapshot;
import com.ezaigc.hr.recruiting.demand.application.assembler.DemandCatalogAssembler;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdCodeOptionEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdCodeOptionPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class JdCodeOptionsTest {
  private final DemandCatalogApplicationManager manager = new DemandCatalogApplicationManager();
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final JdCodeOptionPersistenceService persistence = mock(JdCodeOptionPersistenceService.class);
  private final JdEntityPersistenceService allocation = mock(JdEntityPersistenceService.class);
  private final DemandAuditEventPersistenceService audit = mock(DemandAuditEventPersistenceService.class);

  @BeforeEach
  void setup() {
    var ids = mock(PlatformIdGenerator.class);
    when(ids.newId()).thenReturn("O".repeat(26), "A".repeat(26));
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot("U".repeat(26), "HR",
        "hr@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(), Set.of("RECRUITER"),
        Set.of("demand.read", "demand.manage"), false, false));
    ReflectionTestUtils.setField(manager, "authorization", authorization);
    ReflectionTestUtils.setField(manager, "jdEntityPersistenceService", allocation);
    ReflectionTestUtils.setField(manager, "jdCodeOptionPersistenceService", persistence);
    ReflectionTestUtils.setField(manager, "demandAuditEventPersistenceService", audit);
    ReflectionTestUtils.setField(manager, "ids", ids);
    ReflectionTestUtils.setField(manager, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    when(persistence.insertCodeOption(any())).thenReturn(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"JOB_TYPE", "REGION"})
  void normalRecruiterCanAddFirstOptionAndRepeatedTrimmedNameReusesCode(String kind) {
    String name = "REGION".equals(kind) ? "北京市" : "运维";
    var created = manager.createJdCodeOption(kind, "  " + name + "  ");
    assertThat(created.getCode()).isEqualTo("001");
    assertThat(created.getName()).isEqualTo(name);
    var order = inOrder(authorization, allocation, persistence);
    order.verify(authorization).requirePermission("demand.manage");
    order.verify(allocation).lockCodeAllocation();
    order.verify(persistence).findCodeOptionByName(kind, name);
    verify(audit).insertAudit(anyString(), eq("JD_CODE_OPTION_CREATED"),
        anyString(), anyString(), anyString(), anyString(), any());

    when(persistence.findCodeOptionByName(kind, name)).thenReturn(created);
    assertThat(manager.createJdCodeOption(kind, name + " ")).isSameAs(created);
    verify(persistence).insertCodeOption(any());
  }

  @Test
  void fullCityNameReusesLegacyShortNameCodeAndInvalidRegionDoesNotAllocate() {
    var existing = new JdCodeOptionEntity(); existing.setCode("007"); existing.setName("北京");
    when(persistence.findCodeOptionByName("REGION", "北京")).thenReturn(existing);
    assertThat(manager.createJdCodeOption("REGION", "北京市")).isSameAs(existing);
    assertThatThrownBy(() -> manager.createJdCodeOption("REGION", "北京/上海"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_WORK_REGION_INVALID"));
    verify(persistence, never()).insertCodeOption(any());
  }

  @Test
  void eachKindExpandsTo999ThenRefusesWithoutWriting() {
    when(persistence.lastOptionCode("REGION")).thenReturn("998");
    assertThat(manager.createJdCodeOption("REGION", "杭州").getCode()).isEqualTo("999");
    when(persistence.lastOptionCode("REGION")).thenReturn("999");
    assertThatThrownBy(() -> manager.createJdCodeOption("REGION", "北京"))
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_OPTIONS_EXHAUSTED"));
    verify(persistence).insertCodeOption(any());
  }

  @Test
  void unauthorizedCreationFailsBeforeAnyPersistence() {
    doThrow(new SecurityException()).when(authorization).requirePermission("demand.manage");
    assertThatThrownBy(() -> manager.createJdCodeOption("REGION", "杭州"))
        .isInstanceOf(SecurityException.class);
    verifyNoInteractions(persistence, allocation, audit);
  }

  @Test
  void scopedRecruiterCannotEnumeratePrivateJobTitlesThroughCodeOptions() {
    assertThatThrownBy(manager::listJdCodeOptions)
        .isInstanceOfSatisfying(PlatformException.class,
            error -> assertThat(error.code()).isEqualTo("JD_CODE_OPTIONS_SCOPE_REQUIRED"));
    verify(authorization).requirePermission("demand.read");
    verifyNoInteractions(persistence, allocation, audit);
  }

  @ParameterizedTest
  @ValueSource(strings = {"HR_DIRECTOR", "SUPER_ADMIN"})
  void companyWideReadersCanListOptions(String role) {
    when(authorization.currentActor()).thenReturn(new CurrentActorSnapshot("U".repeat(26), "负责人",
        "reader@example.invalid", "D".repeat(26), Set.of(), Set.of(), Set.of(), Set.of(role),
        Set.of("demand.read"), true, false));
    when(persistence.findCodeOptions()).thenReturn(List.of());
    assertThat(new DemandCatalogAssembler().toJdCodeOptionsResponse(manager.listJdCodeOptions()).getRegions()).isEmpty();
    verify(authorization).requirePermission("demand.read");
    verify(persistence).findCodeOptions();
  }

  @Test
  void listingStillChecksReadPermissionBeforeScopeOrPersistence() {
    doThrow(new SecurityException()).when(authorization).requirePermission("demand.read");
    assertThatThrownBy(manager::listJdCodeOptions).isInstanceOf(SecurityException.class);
    verify(authorization, never()).currentActor();
    verifyNoInteractions(persistence, allocation, audit);
  }

  @Test
  void invalidKindOrBlankNameCannotAllocateCodes() {
    assertThatThrownBy(() -> manager.createJdCodeOption("INVALID", "杭州")).isInstanceOf(PlatformException.class);
    assertThatThrownBy(() -> manager.createJdCodeOption("REGION", "  ")).isInstanceOf(PlatformException.class);
    verify(allocation, never()).lockCodeAllocation();
  }
}

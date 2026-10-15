package com.ezaigc.hr.recruiting.demand.application.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.identity.api.IdentityDirectoryService;
import com.ezaigc.hr.identity.api.IdentityDirectoryService.UserReference;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdEntity;
import com.ezaigc.hr.recruiting.demand.service.DemandAuditEventPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandCatalogPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.DemandOwnershipHistoryPersistenceService;
import com.ezaigc.hr.recruiting.demand.service.JdEntityPersistenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JdHandoverContributorTest {
  private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
  private final JdEntityPersistenceService jds = mock(JdEntityPersistenceService.class);
  private final DemandCatalogPersistenceService catalog = mock(DemandCatalogPersistenceService.class);
  private final DemandOwnershipHistoryPersistenceService histories =
      mock(DemandOwnershipHistoryPersistenceService.class);
  private final DemandAuditEventPersistenceService audits =
      mock(DemandAuditEventPersistenceService.class);
  private final IdentityDirectoryService directory = mock(IdentityDirectoryService.class);

  @Test
  void previewsPrivateOwnedJdsAsMandatoryTransferableResources() {
    when(jds.findPrivateOwnedJds("source", 101)).thenReturn(List.of(jd(false, false)));

    var resources = contributor().preview("source");

    assertThat(resources).singleElement().satisfies(resource -> {
      assertThat(resource.getResourceType()).isEqualTo("JD");
      assertThat(resource.getResourceId()).isEqualTo("jd");
      assertThat(resource.isMandatory()).isTrue();
      assertThat(resource.isTransferable()).isTrue();
    });
  }

  @Test
  void rejectsPreviewBeyondTheExplicitLimit() {
    when(jds.findPrivateOwnedJds("source", 101))
        .thenReturn(IntStream.range(0, 101).mapToObj(index -> jd(false, false)).toList());

    assertThatThrownBy(() -> contributor().preview("source"))
        .isInstanceOfSatisfying(
            PlatformException.class,
            exception -> assertThat(exception.code()).isEqualTo("HANDOVER_PREVIEW_LIMIT_EXCEEDED"));
  }

  @Test
  void transfersOnlyOwnershipAndRecordsHistoryAndAudit() {
    when(catalog.findJdForUpdate("jd")).thenReturn(jd(false, false));
    when(directory.activeUser("target"))
        .thenReturn(new UserReference("target", "目标", "target-department"));
    when(jds.transferPrivateOwner(
            "jd", "source", "target", "target-department", 7, "actor", NOW))
        .thenReturn(1);
    when(histories.insertOwnershipHistory(
            "history", "JD", "jd", "source", "target", "source-department",
            "target-department", "离职交接", "actor", NOW))
        .thenReturn(1);
    when(audits.insertAudit(
            "audit", "JD_OWNERSHIP_CHANGED", "actor", "JD", "jd", "离职交接", NOW))
        .thenReturn(1);

    var result = contributor().transfer("jd", "source", "target", "actor", "离职交接");

    assertThat(result.isSucceeded()).isTrue();
    assertThat(result.getOwnershipHistoryId()).isEqualTo("history");
    verify(jds)
        .transferPrivateOwner(
            "jd", "source", "target", "target-department", 7, "actor", NOW);
  }

  @Test
  void legacyPublicFlagDoesNotPreventOwnershipTransfer() {
    when(catalog.findJdForUpdate("jd")).thenReturn(jd(true, false));
    when(directory.activeUser("target"))
        .thenReturn(new UserReference("target", "目标", "target-department"));
    when(jds.transferPrivateOwner(
            "jd", "source", "target", "target-department", 7, "actor", NOW))
        .thenReturn(1);
    when(histories.insertOwnershipHistory(
            "history", "JD", "jd", "source", "target", "source-department",
            "target-department", "离职交接", "actor", NOW))
        .thenReturn(1);
    when(audits.insertAudit(
            "audit", "JD_OWNERSHIP_CHANGED", "actor", "JD", "jd", "离职交接", NOW))
        .thenReturn(1);

    var result = contributor().transfer("jd", "source", "target", "actor", "离职交接");

    assertThat(result.isSucceeded()).isTrue();
  }

  private JdHandoverContributor contributor() {
    var contributor = new JdHandoverContributor();
    ReflectionTestUtils.setField(contributor, "jds", jds);
    ReflectionTestUtils.setField(contributor, "catalog", catalog);
    ReflectionTestUtils.setField(contributor, "ownershipHistory", histories);
    ReflectionTestUtils.setField(contributor, "auditEvents", audits);
    ReflectionTestUtils.setField(contributor, "directory", directory);
    var ids = List.of("history", "audit").iterator();
    ReflectionTestUtils.setField(contributor, "ids", (PlatformIdGenerator) ids::next);
    ReflectionTestUtils.setField(
        contributor, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    return contributor;
  }

  private static JdEntity jd(boolean publicVisible, boolean deleted) {
    var jd = new JdEntity();
    jd.setId("jd");
    jd.setTitle("私有 JD");
    jd.setOwnerUserId("source");
    jd.setOwnerDepartmentId("source-department");
    jd.setPublicVisible(publicVisible);
    jd.setDeleted(deleted);
    jd.setVersion(7);
    return jd;
  }
}

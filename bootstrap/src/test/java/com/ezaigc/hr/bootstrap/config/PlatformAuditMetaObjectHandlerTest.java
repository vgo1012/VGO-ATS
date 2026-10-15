package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.security.CurrentActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

class PlatformAuditMetaObjectHandlerTest {

  private static final Instant NOW = Instant.parse("2026-09-02T10:15:30Z");

  @Test
  void fillsAllAuditFieldsFromCurrentActorOnInsert() {
    var handler =
        new PlatformAuditMetaObjectHandler(
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> Optional.of(new CurrentActor("01KACTOR000000000000000000", Set.of(), false)));
    var row = new AuditRow();

    handler.insertFill(SystemMetaObject.forObject(row));

    assertThat(row.getCreateId()).isEqualTo("01KACTOR000000000000000000");
    assertThat(row.getUpdateId()).isEqualTo("01KACTOR000000000000000000");
    assertThat(row.getCreateTime()).isEqualTo(NOW);
    assertThat(row.getUpdateTime()).isEqualTo(NOW);
  }

  @Test
  void preservesCreationMetadataButRefreshesUpdateMetadata() {
    Instant createTime = NOW.minusSeconds(60);
    var handler =
        new PlatformAuditMetaObjectHandler(
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> Optional.of(new CurrentActor("01KUPDATER0000000000000000", Set.of(), false)));
    var row = new AuditRow();
    row.setCreateId("01KCREATOR0000000000000000");
    row.setCreateTime(createTime);
    row.setUpdateId("01KOLDUSER000000000000000");
    row.setUpdateTime(createTime);

    handler.updateFill(SystemMetaObject.forObject(row));

    assertThat(row.getCreateId()).isEqualTo("01KCREATOR0000000000000000");
    assertThat(row.getCreateTime()).isEqualTo(createTime);
    assertThat(row.getUpdateId()).isEqualTo("01KUPDATER0000000000000000");
    assertThat(row.getUpdateTime()).isEqualTo(NOW);
  }

  @Test
  void usesReservedSystemActorOutsideAnAuthenticatedRequest() {
    var handler =
        new PlatformAuditMetaObjectHandler(Clock.fixed(NOW, ZoneOffset.UTC), Optional::empty);
    var row = new AuditRow();

    handler.insertFill(SystemMetaObject.forObject(row));

    assertThat(row.getCreateId()).isEqualTo(PlatformActorConstants.SYSTEM_ACTOR_ID);
    assertThat(row.getUpdateId()).isEqualTo(PlatformActorConstants.SYSTEM_ACTOR_ID);
  }

  private static final class AuditRow {
    private String createId;
    private Instant createTime;
    private String updateId;
    private Instant updateTime;

    public String getCreateId() {
      return createId;
    }

    public void setCreateId(String createId) {
      this.createId = createId;
    }

    public Instant getCreateTime() {
      return createTime;
    }

    public void setCreateTime(Instant createTime) {
      this.createTime = createTime;
    }

    public String getUpdateId() {
      return updateId;
    }

    public void setUpdateId(String updateId) {
      this.updateId = updateId;
    }

    public Instant getUpdateTime() {
      return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
      this.updateTime = updateTime;
    }
  }
}

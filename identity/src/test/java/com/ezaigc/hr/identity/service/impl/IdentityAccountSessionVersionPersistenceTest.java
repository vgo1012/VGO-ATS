package com.ezaigc.hr.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.mapper.IdentityAccountMapper;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityAccountSessionVersionPersistenceTest {

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void advancesVersionAtomicallyOnlyForTheTargetActiveAccount() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        UserAccountRecord.class);
    IdentityAccountMapper mapper = mock(IdentityAccountMapper.class);
    IdentityAccountPersistenceServiceImpl service = new IdentityAccountPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<UserAccountRecord>> updates =
        ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(updates.capture())).thenReturn(1);
    Instant now = Instant.parse("2026-09-01T12:00:00Z");

    assertThat(service.advanceSessionVersion("target", "actor", now)).isOne();

    LambdaUpdateWrapper<UserAccountRecord> update = updates.getValue();
    assertThat(update.getSqlSet()).contains("version=version + 1", "update_id", "update_time");
    assertThat(update.getSqlSegment()).contains("id =", "status =");
    assertThat(update.getParamNameValuePairs().values()).contains("target", "actor", "ACTIVE", now);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void successfulLoginCleanupKeepsTheAuthenticatedAccountVersion() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
        UserAccountRecord.class);
    IdentityAccountMapper mapper = mock(IdentityAccountMapper.class);
    IdentityAccountPersistenceServiceImpl service = new IdentityAccountPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    ArgumentCaptor<LambdaUpdateWrapper<UserAccountRecord>> updates =
        ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
    when(mapper.update(updates.capture())).thenReturn(1);

    assertThat(service.clearLoginFailures("target")).isOne();

    assertThat(updates.getValue().getSqlSet()).doesNotContain("version");
  }
}

package com.ezaigc.hr.recruiting.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.Communication;
import com.ezaigc.hr.recruiting.recruitment.mapper.CommunicationMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.CommunicationPersistenceServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CommunicationCurrentReadTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void replayReadsOnlyTheTargetUsingCurrentLockRead() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Communication.class);
    var mapper = mock(CommunicationMapper.class);
    var service = new CommunicationPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    var record = new Communication();
    when(mapper.selectOne(any(), eq(true))).thenReturn(record);
    String id = "01J" + "A".repeat(23);
    assertThat(service.findByIdForUpdate(id)).isSameAs(record);
    ArgumentCaptor<LambdaQueryWrapper<Communication>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(query.capture(), eq(true));
    assertThat(query.getValue().getSqlSegment()).contains("id =", "FOR UPDATE");
    assertThat(query.getValue().getParamNameValuePairs().values()).containsExactly(id);
  }
}

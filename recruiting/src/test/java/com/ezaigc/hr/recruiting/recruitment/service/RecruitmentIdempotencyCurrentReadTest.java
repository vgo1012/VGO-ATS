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
import com.ezaigc.hr.recruiting.recruitment.domain.entity.IdempotencyRecord;
import com.ezaigc.hr.recruiting.recruitment.mapper.IdempotencyRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.RecruitmentIdempotencyPersistenceServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RecruitmentIdempotencyCurrentReadTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void completionReplayReadsCurrentRecordUnderActorAndKeyLock() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), IdempotencyRecord.class);
    var mapper = mock(IdempotencyRecordMapper.class);
    var service = new RecruitmentIdempotencyPersistenceServiceImpl();
    ReflectionTestUtils.setField(service,"baseMapper",mapper);
    var record = new IdempotencyRecord();
    when(mapper.selectOne(any(),eq(true))).thenReturn(record);
    assertThat(service.findIdempotencyForUpdate("actor-1","key-1")).isSameAs(record);
    ArgumentCaptor<LambdaQueryWrapper<IdempotencyRecord>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(query.capture(),eq(true));
    assertThat(query.getValue().getSqlSegment()).contains("actor_user_id","idempotency_key","FOR UPDATE");
    assertThat(query.getValue().getParamNameValuePairs().values()).containsExactlyInAnyOrder("actor-1","key-1");
  }
}

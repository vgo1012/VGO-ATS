package com.ezaigc.hr.talent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.mapper.CandidateMapper;
import java.time.Instant;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidatePersistenceServiceContactTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void clearingContactsWritesNullInsteadOfSilentlyPreservingOldValues() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Candidate.class);
    var mapper = mock(CandidateMapper.class);
    var service = new CandidatePersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    service.updateCandidate("candidate", "姓名", null, null, "owner", "department", 3,
        "actor", Instant.now());
    ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).update(any(Candidate.class), wrapper.capture());
    assertThat(wrapper.getValue().getSqlSet()).contains("primary_email=", "primary_phone=");
    assertThat(wrapper.getValue().getSqlSegment()).contains("id", "status");
  }
}

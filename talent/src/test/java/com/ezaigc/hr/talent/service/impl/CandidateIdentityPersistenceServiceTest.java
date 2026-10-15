package com.ezaigc.hr.talent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.talent.domain.entity.Identity;
import com.ezaigc.hr.talent.mapper.CandidateIdentityMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateIdentityPersistenceServiceTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void deletingAnOldPrimaryIdentityRequiresCandidateTypeAndValueHash() {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Identity.class);
    var mapper = mock(CandidateIdentityMapper.class);
    var service = new CandidateIdentityPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);

    service.deleteIdentity("candidate", "PHONE", "old-hash");

    ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).delete(wrapper.capture());
    assertThat(wrapper.getValue().getSqlSegment())
        .contains("candidate_id", "identity_type", "normalized_value_hash");
    assertThat(((AbstractWrapper) wrapper.getValue()).getParamNameValuePairs().values())
        .contains("candidate", "PHONE", "old-hash");
  }
}

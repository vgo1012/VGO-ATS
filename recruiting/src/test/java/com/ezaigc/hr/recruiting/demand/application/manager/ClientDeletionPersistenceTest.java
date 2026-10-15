package com.ezaigc.hr.recruiting.demand.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.demand.domain.entity.ClientEntity;
import com.ezaigc.hr.recruiting.demand.mapper.DemandCatalogMapper;
import com.ezaigc.hr.recruiting.demand.service.impl.DemandCatalogPersistenceServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ClientDeletionPersistenceTest {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void deleteIsRestrictedToExactIdAndVersionWithoutStatusOrCascade() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "client-delete"), ClientEntity.class);
    var mapper = mock(DemandCatalogMapper.class);
    var service = new DemandCatalogPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    when(mapper.delete(any(Wrapper.class))).thenReturn(1);
    assertThat(service.deleteClient("client", 7)).isEqualTo(1);
    ArgumentCaptor<Wrapper<ClientEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
    verify(mapper).delete(captor.capture());
    var query = (LambdaQueryWrapper<ClientEntity>) captor.getValue();
    assertThat(query.getSqlSegment()).contains("id =", "version =").doesNotContain("status");
    assertThat(query.getParamNameValuePairs()).containsValues("client", 7L).hasSize(2);
    verifyNoMoreInteractions(mapper);
  }
}

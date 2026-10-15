package com.ezaigc.hr.recruiting.recruitment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.recruiting.recruitment.domain.entity.OfferStatusRecord;
import com.ezaigc.hr.recruiting.recruitment.mapper.OfferStatusRecordMapper;
import com.ezaigc.hr.recruiting.recruitment.service.impl.OfferStatusRecordPersistenceServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OfferStatusRecordPersistenceServiceImplTest {
  private final OfferStatusRecordMapper mapper = mock(OfferStatusRecordMapper.class);
  private final OfferStatusRecordPersistenceServiceImpl service = new OfferStatusRecordPersistenceServiceImpl();

  @BeforeEach
  void setUp() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), OfferStatusRecord.class);
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void currentOfferUsesAppendTimeInsteadOfClientReportedBusinessTime() {
    service.latestOffer("A".repeat(26));

    ArgumentCaptor<LambdaQueryWrapper<OfferStatusRecord>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectOne(captor.capture(), eq(true));
    var query = captor.getValue();
    assertThat(query.getSqlSegment()).contains("application_id =", "ORDER BY create_time DESC,id DESC", "LIMIT 1")
        .doesNotContain("occurred_at");
    assertThat(query.getParamNameValuePairs().values()).containsExactly("A".repeat(26));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void historyKeepsAppendOrderSoItsLastRecordAgreesWithTheCurrentOffer() {
    service.findOffers("A".repeat(26));

    ArgumentCaptor<LambdaQueryWrapper<OfferStatusRecord>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
    verify(mapper).selectList(captor.capture());
    assertThat(captor.getValue().getSqlSegment()).contains("ORDER BY create_time ASC,id ASC")
        .doesNotContain("occurred_at");
  }
}

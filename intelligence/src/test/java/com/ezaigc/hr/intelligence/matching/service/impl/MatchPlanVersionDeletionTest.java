package com.ezaigc.hr.intelligence.matching.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.mapper.MatchPlanVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class MatchPlanVersionDeletionTest {
  @ParameterizedTest
  @CsvSource({"0, 0", "7, 1"})
  void deletesOnlyMatchingTechnicalVersionOfTheDraftAndReturnsAffectedRows(long version, int rows) {
    TableInfoHelper.initTableInfo(
        new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MatchPlanVersion.class);
    var mapper = mock(MatchPlanVersionMapper.class);
    when(mapper.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
      LambdaQueryWrapper<MatchPlanVersion> query = invocation.getArgument(0);
      assertThat(query.getSqlSegment()).contains("id =", "version =", "status =");
      assertThat(query.getParamNameValuePairs().values())
          .containsExactlyInAnyOrder("version-1", version, "DRAFT");
      return rows;
    });
    var service = new MatchPlanVersionPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);

    assertThat(service.deleteMatchPlanVersion("version-1", version)).isEqualTo(rows);
  }
}

package com.ezaigc.hr.intelligence.matching.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ezaigc.hr.intelligence.matching.constant.DefaultScoringConstants;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchPlanVersion;
import com.ezaigc.hr.intelligence.matching.mapper.MatchPlanVersionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LatestCompanyScoringTest {
  @Test void selectsLatestActivatedVersionOfCompanyPlanInsteadOfPinnedV1() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MatchPlanVersion.class);
    var mapper = mock(MatchPlanVersionMapper.class);
    var baseline = new MatchPlanVersion(); baseline.setPlanId("company-plan");
    var latest = new MatchPlanVersion(); latest.setId("version-2"); latest.setVersionNo(2);
    when(mapper.selectById(DefaultScoringConstants.PLAN_VERSION_ID)).thenReturn(baseline);
    when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
      Page<MatchPlanVersion> page = invocation.getArgument(0);
      LambdaQueryWrapper<MatchPlanVersion> query = invocation.getArgument(1);
      assertThat(page.getSize()).isEqualTo(1);
      assertThat(query.getSqlSegment()).contains("plan_id", "status", "ORDER BY version_no DESC");
      assertThat(query.getParamNameValuePairs()).containsValue("company-plan").containsValue("ACTIVE");
      page.setRecords(List.of(latest)); return page;
    });
    var service = new MatchPlanVersionPersistenceServiceImpl();
    ReflectionTestUtils.setField(service, "baseMapper", mapper);
    assertThat(service.findActiveMatchPlanVersion("some-jd")).isSameAs(latest);
  }
}

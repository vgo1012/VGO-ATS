package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarAttemptMapper;
import com.ezaigc.hr.recruiting.interview.mapper.InterviewExternalCalendarProjectionMapper;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

class MybatisPlatformConfigurationTest {

  @Test
  void mapperBeansUseFullyQualifiedNamesAcrossModules() {
    MapperScan mapperScan = MybatisPlatformConfiguration.class.getAnnotation(MapperScan.class);

    assertThat(mapperScan.nameGenerator())
        .isEqualTo(FullyQualifiedAnnotationBeanNameGenerator.class);
  }

  @Test
  void externalCalendarMappersAreIncludedByProductionScan() {
    assertThat(InterviewExternalCalendarAttemptMapper.class).hasAnnotation(Mapper.class);
    assertThat(InterviewExternalCalendarProjectionMapper.class).hasAnnotation(Mapper.class);
  }

  @Test
  void keepsSafetyAndConcurrencyInterceptorsBeforePagination() {
    MybatisPlusInterceptor interceptor =
        new MybatisPlatformConfiguration().mybatisPlusInterceptor();

    assertThat(interceptor.getInterceptors())
        .hasSize(3)
        .element(0)
        .isInstanceOf(BlockAttackInnerInterceptor.class);
    assertThat(interceptor.getInterceptors())
        .element(1)
        .isInstanceOf(OptimisticLockerInnerInterceptor.class);
    assertThat(interceptor.getInterceptors().get(1)).extracting("wrapperMode").isEqualTo(true);
    assertThat(interceptor.getInterceptors())
        .element(2)
        .isInstanceOf(PaginationInnerInterceptor.class);

    PaginationInnerInterceptor pagination =
        (PaginationInnerInterceptor) interceptor.getInterceptors().get(2);
    assertThat(pagination.isOverflow()).isFalse();
    assertThat(pagination.getMaxLimit()).isEqualTo(500L);
  }
}

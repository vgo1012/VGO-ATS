package com.ezaigc.hr.bootstrap.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ezaigc.hr.common.core.security.CurrentActorProvider;
import com.ezaigc.hr.common.web.pagination.PageLimits;
import java.time.Clock;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = {
                "com.ezaigc.hr.identity",
                "com.ezaigc.hr.recruiting.demand",
                "com.ezaigc.hr.recruiting.activity",
                "com.ezaigc.hr.talent",
                "com.ezaigc.hr.recruiting.recruitment",
                "com.ezaigc.hr.recruiting.interview",
                "com.ezaigc.hr.recruiting.submission",
                "com.ezaigc.hr.intelligence.matching",
                "com.ezaigc.hr.intelligence.search",
                "com.ezaigc.hr.integration",
                "com.ezaigc.hr.reporting"
        },
        annotationClass = Mapper.class,
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class MybatisPlatformConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor(true));
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(false);
        pagination.setMaxLimit((long) PageLimits.MAXIMUM_PHYSICAL_FETCH);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    MetaObjectHandler platformAuditMetaObjectHandler(
            Clock clock, CurrentActorProvider currentActorProvider) {
        return new PlatformAuditMetaObjectHandler(clock, currentActorProvider);
    }
}

package com.ezaigc.hr.integration.config;

import com.ezaigc.hr.intelligence.matching.application.provider.ProfessionalScoringModelPort;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort;
import com.ezaigc.hr.recruiting.demand.application.provider.JdParseModelPort.ModelUnavailableException;
import com.ezaigc.hr.talent.application.provider.ResumeMultimodalPagePort;
import com.ezaigc.hr.talent.application.provider.ResumeOcrPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DisabledIntegrationConfiguration {

  @Bean
  @ConditionalOnMissingBean(JdParseModelPort.class)
  JdParseModelPort unavailableJdParseModelPort() {
    return request -> {
      throw new ModelUnavailableException("JD 解析模型尚未由管理员启用");
    };
  }

  @Bean
  @ConditionalOnMissingBean(ResumeParseModelPort.class)
  ResumeParseModelPort unavailableResumeParseModelPort() {
    return request -> {
      throw new ResumeParseModelPort.ModelUnavailableException("简历解析模型尚未由管理员启用");
    };
  }

  @Bean
  @ConditionalOnMissingBean(ProfessionalScoringModelPort.class)
  ProfessionalScoringModelPort unavailableProfessionalScoringModelPort() {
    return request -> {
      throw new ProfessionalScoringModelPort.ModelUnavailableException("AI 专业评分模型尚未由管理员启用");
    };
  }

  @Bean
  @ConditionalOnMissingBean
  ResumeOcrPort disabledResumeOcrPort() {
    return (pageNo, image) -> Optional.empty();
  }

  @Bean
  @ConditionalOnMissingBean
  ResumeMultimodalPagePort disabledResumeMultimodalPagePort() {
    return (pageNo, image) -> Optional.empty();
  }
}

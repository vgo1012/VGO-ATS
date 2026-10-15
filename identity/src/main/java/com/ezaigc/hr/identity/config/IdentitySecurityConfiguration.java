package com.ezaigc.hr.identity.config;

import com.ezaigc.hr.identity.application.manager.IdentitySessionApplicationManager;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import com.ezaigc.hr.identity.domain.response.SecurityProblemResponses;

@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {

  @Resource private IdentitySessionApplicationManager sessions;

  @Bean
  SecurityFilterChain platformSecurityFilterChain(
      HttpSecurity http, CsrfTokenRepository csrfTokenRepository) throws Exception {
    http.authorizeHttpRequests(
        authorize ->
            authorize
                .requestMatchers("/actuator/health/**")
                .permitAll()
                .requestMatchers("/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf")
                .permitAll()
                .requestMatchers("/api/v1/admin/**")
                .hasRole("SUPER_ADMIN")
                .anyRequest()
                .authenticated());
    http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository));
    http.securityContext(context -> context.requireExplicitSave(true));
    http.sessionManagement(
        session -> session.sessionFixation(fixation -> fixation.migrateSession()));
    http.exceptionHandling(
        exceptions ->
            exceptions
                .authenticationEntryPoint(
                    (request, response, exception) ->
                        SecurityProblemResponses
                            .unauthorized(response))
                .accessDeniedHandler(
                    (request, response, exception) ->
                        SecurityProblemResponses.forbidden(
                            response, exception)));
    http.addFilterBefore(new IdentitySessionFilter(sessions), CsrfFilter.class);
    http.addFilterBefore(new MustChangePasswordFilter(), AuthorizationFilter.class);
    http.httpBasic(AbstractHttpConfigurer::disable);
    http.formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
    repository.setHeaderName("X-CSRF-TOKEN");
    return repository;
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
}

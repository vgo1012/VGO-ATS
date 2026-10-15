package com.ezaigc.hr.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

class IdentityBootstrapAdminPropertiesTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void toStringDoesNotExposeBootstrapPassword() {
    String password = "do-not-log-this-secret";
    String workEmail = "private-admin@example.com";
    IdentityBootstrapAdminProperties properties = new IdentityBootstrapAdminProperties();
    properties.setPassword(password);
    properties.setWorkEmail(workEmail);

    assertThat(properties.toString()).doesNotContain(password, workEmail);
  }

  @Test
  void rejectsIncompleteConfigurationWhenBootstrapIsEnabled() {
    IdentityBootstrapAdminProperties properties = new IdentityBootstrapAdminProperties();
    BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(properties);

    assertThat(wrapper.isWritableProperty("enabled"))
        .as("配置对象必须绑定 enabled，才能实施条件校验")
        .isTrue();
    wrapper.setPropertyValue("enabled", true);

    assertThat(validator.validate(properties)).isNotEmpty();
  }

  @Test
  void acceptsBlankSecretsWhileBootstrapIsDisabled() {
    assertThat(validator.validate(new IdentityBootstrapAdminProperties())).isEmpty();
  }

  @Test
  void acceptsCompleteConfigurationWhenBootstrapIsEnabled() {
    IdentityBootstrapAdminProperties properties = new IdentityBootstrapAdminProperties();
    properties.setEnabled(true);
    properties.setDepartmentCode("ROOT");
    properties.setDepartmentName("总部");
    properties.setDisplayName("系统管理员");
    properties.setWorkEmail("admin@example.com");
    properties.setPassword("test-only-secret-123");

    assertThat(validator.validate(properties)).isEmpty();
  }
}

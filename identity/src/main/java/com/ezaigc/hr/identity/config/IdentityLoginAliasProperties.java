package com.ezaigc.hr.identity.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("hr.identity.login-alias")
public class IdentityLoginAliasProperties {

  @Email
  @Size(max = 254)
  private String adminWorkEmail = "";
}

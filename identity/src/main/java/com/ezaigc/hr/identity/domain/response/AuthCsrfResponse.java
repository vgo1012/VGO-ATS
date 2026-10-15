package com.ezaigc.hr.identity.domain.response;

import jakarta.validation.constraints.*;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCsrfResponse {
  private String headerName;
  private String parameterName;
  /** 令牌。 */
  @ToString.Exclude
  private String token;

}

package com.ezaigc.hr.recruiting.recruitment.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationVersionRequest {
  private @ValidResourceVersion String version;

}

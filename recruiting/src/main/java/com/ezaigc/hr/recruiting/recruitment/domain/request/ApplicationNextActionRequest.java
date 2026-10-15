package com.ezaigc.hr.recruiting.recruitment.domain.request;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class ApplicationNextActionRequest {
  @NotBlank @Size(max = 64) private String stepCode;
  @Min(1) private Integer roundNo;
  @Size(max = 500) private String reason;
  @ValidResourceVersion private String version;
}

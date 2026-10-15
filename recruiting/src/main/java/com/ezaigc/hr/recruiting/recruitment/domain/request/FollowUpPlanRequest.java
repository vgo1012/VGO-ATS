package com.ezaigc.hr.recruiting.recruitment.domain.request;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.Data;
@Data
public class FollowUpPlanRequest {
  @NotNull private Instant startsAt;
  @NotNull private Instant endsAt;
  @NotBlank @Size(max = 32) private String channel;
  @Size(max = 500) private String note;
  @ValidResourceVersion private String version;
}

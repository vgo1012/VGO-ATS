package com.ezaigc.hr.recruiting.recruitment.domain.request;

import static com.ezaigc.hr.recruiting.recruitment.constant.RecruitmentConstants.MAX_COLLABORATORS_PER_APPLICATION;

import com.ezaigc.hr.common.web.request.PlatformIdentifier;
import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCollaboratorRequest {
  private @Size(max = MAX_COLLABORATORS_PER_APPLICATION) List<@PlatformIdentifier String>
      collaboratorUserIds;
  private @ValidResourceVersion String version;
  private @NotBlank @Size(max = 500) String reason;

}

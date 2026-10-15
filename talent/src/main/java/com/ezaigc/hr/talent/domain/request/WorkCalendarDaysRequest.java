package com.ezaigc.hr.talent.domain.request;

import com.ezaigc.hr.common.web.version.ValidResourceVersion;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarDaysRequest {
  private @ValidResourceVersion String expectedVersion;
  private @NotEmpty @Size(max = 366) List<@NotNull LocalDate> dates;
  private @NotBlank @Size(max = 7) @Pattern(regexp = "WORKDAY|RESTDAY|HOLIDAY") String dayType;
  private @Size(max = 200) String description;
}

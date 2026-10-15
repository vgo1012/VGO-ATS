package com.ezaigc.hr.talent.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 完整年度日历，保存即生效；首次保存两个基线字段均为空。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkCalendarSaveRequest {
  private @Size(min = 26, max = 26) String publishedRevisionId;
  private @Size(max = 19) @Pattern(regexp = "0|[1-9][0-9]{0,18}") String expectedVersion;
  private @NotNull @Size(min = 365, max = 366) List<@NotNull @Valid DayRequest> days;
  private @NotBlank @Size(max = 1000) String sourceNote;
  private @NotBlank @Size(max = 500) String reason;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DayRequest {
    private @NotNull LocalDate date;
    private @NotNull @Size(max = 7) @Pattern(regexp = "WORKDAY|RESTDAY|HOLIDAY") String dayType;
    private @Size(max = 200) String description;
  }
}

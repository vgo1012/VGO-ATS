package com.ezaigc.hr.recruiting.recruitment.domain.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 已入职候选人条件；区间与简历库保持一致，未知事实不推断。 */
@Data
public class ApplicationCandidateFilterRequest {
  @Size(max = 500) private String q;
  /** 自定义全文条件，全部同时满足。 */
  @Size(max = 10) private List<@NotBlank @Size(max = 200) String> conditions = new ArrayList<>();
  @Size(max = 50) private List<@NotNull @Size(max = 200) @Pattern(regexp = ".*\\S.*") String> degrees = new ArrayList<>();
  @DecimalMin("0") @DecimalMax("80") private BigDecimal minWorkYears;
  @DecimalMin("0") @DecimalMax("80") private BigDecimal maxWorkYears;
  @Min(16) @Max(100) private Integer minAge;
  @Min(16) @Max(100) private Integer maxAge;
  @Pattern(regexp = "(?i)MALE|FEMALE|OTHER") private String gender;

  @AssertTrue(message = "最小年龄不能大于最大年龄")
  public boolean isAgeRangeValid() { return minAge == null || maxAge == null || minAge <= maxAge; }
  @AssertTrue(message = "工作年限区间不能为空")
  public boolean isWorkYearsRangeValid() {
    return minWorkYears == null || maxWorkYears == null || minWorkYears.compareTo(maxWorkYears) < 0;
  }
}

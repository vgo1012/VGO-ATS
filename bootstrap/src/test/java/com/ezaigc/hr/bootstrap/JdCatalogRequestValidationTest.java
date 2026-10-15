package com.ezaigc.hr.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.recruiting.demand.domain.request.CreateJdVersionRequest;
import com.ezaigc.hr.recruiting.demand.domain.request.UpdateJdVersionRequest;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JdCatalogRequestValidationTest {
  @Test
  void manualCreationNeedsExplicitRegionWithoutCodeSelectors() {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setDistinction("北京一组");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    request.setOwnerDepartmentId("D".repeat(26));
    request.setRecruitmentType("INTERNAL");
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request, CreateJdVersionRequest.NewJd.class))
          .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("workRegionName");
      request.setWorkRegionName("北京");
      assertThat(factory.getValidator().validate(request, CreateJdVersionRequest.NewJd.class)).isEmpty();
    }
  }

  @Test
  void automaticCodeInputLimitsApplyOnlyToNewManualJds() {
    var request = new CreateJdVersionRequest();
    request.setTitle("岗".repeat(81)); request.setWorkRegionName("区".repeat(81));
    request.setDistinction("北京"); request.setRawText("原文");
    request.setHeadcount(1); request.setRecruitmentCycleDays(45);
    request.setOwnerDepartmentId("D".repeat(26)); request.setRecruitmentType("INTERNAL");
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request, CreateJdVersionRequest.NewJd.class))
          .extracting(violation -> violation.getPropertyPath().toString())
          .containsExactlyInAnyOrder("title", "workRegionName");
      request.setTitle("岗".repeat(80)); request.setWorkRegionName("区".repeat(80));
      assertThat(factory.getValidator().validate(request, CreateJdVersionRequest.NewJd.class)).isEmpty();
      var edit = update("1"); edit.setTitle("岗".repeat(180));
      assertThat(factory.getValidator().validate(edit)).isEmpty();
    }
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {"00", "000", "1", "1000", "AB", " 01"})
  void suppliedCodeDimensionsRejectInvalidFormats(String code) {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setDistinction("北京一组");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    request.setOwnerDepartmentId("D".repeat(26));
    request.setRecruitmentType("INTERNAL");
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      request.setJobTypeCode(code);
      request.setWorkRegionCode(code);
      assertThat(validator.validate(request, CreateJdVersionRequest.NewJd.class).stream()
          .map(violation -> violation.getPropertyPath().toString())
          .toList()).contains("jobTypeCode", "workRegionCode");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"01", "99", "001", "100", "999"})
  void suppliedCodeDimensionsAcceptLegacyAndThreeDigitCodes(String code) {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setWorkRegionName("杭州");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    request.setOwnerDepartmentId("D".repeat(26));
    request.setRecruitmentType("INTERNAL");
    request.setJobTypeCode(code);
    request.setWorkRegionCode(code);
    var update = update("1");
    update.setJobTypeCode(code);
    update.setWorkRegionCode(code);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request, CreateJdVersionRequest.NewJd.class)).isEmpty();
      assertThat(factory.getValidator().validate(update)).isEmpty();
    }
  }

  @Test
  void newJdNeedsTitleOriginalTextHeadcountAndRecruitmentCycle() {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setDistinction("北京一组");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    request.setOwnerDepartmentId("D".repeat(26));
    request.setRecruitmentType("INTERNAL");
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request)).isEmpty();
    }
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void createAndUpdateAllowMissingDistinction(String distinction) {
    var create = new CreateJdVersionRequest();
    create.setTitle("运维工程师");
    create.setDistinction(distinction);
    create.setWorkRegionName("北京");
    create.setOwnerDepartmentId("D".repeat(26));
    create.setRecruitmentType("INTERNAL");
    create.setRawText("负责 Linux 系统维护");
    create.setHeadcount(1);
    create.setRecruitmentCycleDays(45);
    var update = update("1");
    update.setDistinction(distinction);

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(create, CreateJdVersionRequest.NewJd.class)).isEmpty();
      assertThat(validator.validate(update)).isEmpty();
    }
  }

  @Test
  void newJdRequiresDepartmentAndSupportedRecruitmentType() {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setDistinction("北京一组");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(request, CreateJdVersionRequest.NewJd.class)).extracting(
          violation -> violation.getPropertyPath().toString())
          .contains("ownerDepartmentId", "recruitmentType");
      request.setOwnerDepartmentId("D".repeat(26));
      request.setRecruitmentType("PARTNER");
      assertThat(validator.validate(request, CreateJdVersionRequest.NewJd.class)).anySatisfy(violation ->
          assertThat(violation.getPropertyPath().toString()).isEqualTo("recruitmentType"));
    }
  }

  @Test
  void distinctionAccepts180CharactersAndRejects181() {
    var create = new CreateJdVersionRequest();
    create.setTitle("运维工程师");
    create.setDistinction("区".repeat(180));
    create.setRawText("负责 Linux 系统维护");
    create.setHeadcount(1);
    create.setRecruitmentCycleDays(45);
    create.setOwnerDepartmentId("D".repeat(26));
    create.setRecruitmentType("INTERNAL");
    create.setWorkRegionName("北京");
    var update = update("1");
    update.setDistinction("区".repeat(180));

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(create, CreateJdVersionRequest.NewJd.class)).isEmpty();
      assertThat(validator.validate(update)).isEmpty();
      create.setDistinction("区".repeat(181));
      update.setDistinction("区".repeat(181));
      assertThat(validator.validate(create, CreateJdVersionRequest.NewJd.class)).anySatisfy(violation ->
          assertThat(violation.getPropertyPath().toString()).isEqualTo("distinction"));
      assertThat(validator.validate(update)).anySatisfy(violation ->
          assertThat(violation.getPropertyPath().toString()).isEqualTo("distinction"));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 10001})
  void headcountMustBeWithinSupportedRange(int headcount) {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(headcount);
    request.setRecruitmentCycleDays(45);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("headcount"));
    }
  }

  @Test
  void headcountRejectsMissingAndFloatingPointJson() throws Exception {
    var mapper = new ObjectMapper();
    var missing = mapper.readValue("{\"title\":\"运维工程师\",\"rawText\":\"Linux\"}",
        CreateJdVersionRequest.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(missing))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("headcount"));
    }
    assertThatThrownBy(() -> mapper.readValue(
        "{\"title\":\"运维工程师\",\"rawText\":\"Linux\",\"headcount\":1.5,\"recruitmentCycleDays\":45}",
        CreateJdVersionRequest.class)).hasMessageContaining("必须是 JSON 整数");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 3651})
  void recruitmentCycleMustBeWithinSupportedRange(int days) {
    var request = new CreateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(days);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
              .isEqualTo("recruitmentCycleDays"));
    }
  }

  @Test
  void recruitmentCycleRejectsMissingAndFloatingPointJson() throws Exception {
    var mapper = new ObjectMapper();
    var missing = mapper.readValue(
        "{\"title\":\"运维工程师\",\"rawText\":\"Linux\",\"headcount\":1}",
        CreateJdVersionRequest.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(missing))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
              .isEqualTo("recruitmentCycleDays"));
    }
    assertThatThrownBy(() -> mapper.readValue(
        "{\"title\":\"运维工程师\",\"rawText\":\"Linux\",\"headcount\":1,\"recruitmentCycleDays\":45.5}",
        CreateJdVersionRequest.class)).hasMessageContaining("必须是 JSON 整数");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"v1", "-1", " 1", "9223372036854775808"})
  void updateRejectsMissingMalformedOrOverflowingVersions(String version) {
    var request = update(version);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertThat(factory.getValidator().validate(request))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("version"));
    }
  }

  @Test
  void updateAcceptsSystemVersionAndOptionalOwnershipFieldsWhileValidatingContent() {
    var request = update("0");
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      assertThat(validator.validate(request)).isEmpty();
      request.setRawText(" ");
      assertThat(validator.validate(request))
          .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("rawText"));
    }
  }

  private UpdateJdVersionRequest update(String version) {
    var request = new UpdateJdVersionRequest();
    request.setTitle("运维工程师");
    request.setDistinction("北京一组");
    request.setRawText("负责 Linux 系统维护");
    request.setHeadcount(1);
    request.setRecruitmentCycleDays(45);
    request.setVersion(version);
    return request;
  }
}

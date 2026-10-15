package com.ezaigc.hr.intelligence.search.domain.request;

import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_CANDIDATE_AGE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_CURSOR_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUE_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_FILTER_VALUES;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_NATURAL_CONDITION_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_QUERY_LENGTH;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_SEARCH_PAGE_SIZE;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MAX_WORK_YEARS;
import static com.ezaigc.hr.intelligence.search.constant.SearchConstants.MIN_CANDIDATE_AGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.intelligence.search.domain.dto.SearchCriteria;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

class TalentSearchRequestValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    validatorFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void boundsCustomConditionsAndRejectsBlankValues() {
    var request = new TalentSearchRequest();
    request.setConditions(List.of("高压电工证", "低压电工证"));
    assertThat(validator.validate(request)).isEmpty();
    request.setConditions(Collections.nCopies(11, "证书"));
    assertThat(paths(validator.validate(request))).contains("conditions");
    request.setConditions(List.of(" ", "x".repeat(201)));
    assertThat(validator.validate(request)).hasSize(2);
    var criteria = new SearchCriteria();
    criteria.setConditions(Collections.nCopies(11, "证书"));
    assertThat(paths(validator.validate(criteria))).contains("conditions");
  }

  @Test
  void jdFilterSupportsSharedPersonalPoolAndMyTasks() {
    var request = new TalentSearchRequest();
    request.setJdId("jd");
    assertThat(validator.validate(request)).isEmpty();
    request.setScope("MY_TASKS");
    assertThat(validator.validate(request)).isEmpty();
    request.setScope("PERSONAL_POOL");
    assertThat(validator.validate(request)).isEmpty();
    request.setJdId(" ");
    assertThat(paths(validator.validate(request))).contains("jdId");
    request.setJdId("j".repeat(65));
    assertThat(paths(validator.validate(request))).contains("jdId");
  }

  @Test
  void internalCriteriaRetainsTheSameJobScopeBoundary() {
    var criteria = new SearchCriteria();
    criteria.setJdId("jd");
    assertThat(validator.validate(criteria)).isEmpty();
    criteria.setScope("PERSONAL_POOL");
    assertThat(validator.validate(criteria)).isEmpty();
    criteria.setScope("MY_TASKS");
    assertThat(validator.validate(criteria)).isEmpty();
  }

  @Test
  void rejectsOversizedSearchTextAndCursor() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setQ("Q".repeat(MAX_QUERY_LENGTH + 1));
    request.setNaturalCondition("N".repeat(MAX_NATURAL_CONDITION_LENGTH + 1));
    request.setCursor("C".repeat(MAX_CURSOR_LENGTH + 1));

    assertThat(paths(validator.validate(request))).contains("q", "naturalCondition", "cursor");
  }

  @Test
  void rejectsOversizedOrMalformedStructuredFilters() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setSkills(Collections.nCopies(MAX_FILTER_VALUES + 1, "Java"));
    request.setEmployers(List.of("E".repeat(MAX_FILTER_VALUE_LENGTH + 1)));
    request.setTags(List.of(" "));
    request.setGender("INFERRED_FROM_NAME");
    request.setCandidateIds(Collections.nCopies(201, "candidate"));

    assertThat(paths(validator.validate(request)))
        .anyMatch(path -> path.startsWith("skills"))
        .anyMatch(path -> path.startsWith("employers"))
        .anyMatch(path -> path.startsWith("tags"))
        .contains("gender", "candidateIds");
  }

  @Test
  void rejectsOutOfDomainNumericFilters() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setMinWorkYears(BigDecimal.valueOf(-1));
    request.setMinAge(MIN_CANDIDATE_AGE - 1);
    request.setMaxAge(MAX_CANDIDATE_AGE + 1);
    request.setLimit(0);

    assertThat(paths(validator.validate(request)))
        .contains("minWorkYears", "minAge", "maxAge", "limit");
  }

  @Test
  void rejectsReversedAgeAndUpdateRanges() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setMinAge(40);
    request.setMaxAge(30);
    request.setUpdatedFrom(Instant.parse("2026-09-04T02:00:00Z"));
    request.setUpdatedTo(Instant.parse("2026-09-04T01:00:00Z"));

    assertThat(paths(validator.validate(request)))
        .contains("ageRangeValid", "updatedRangeValid");
  }

  @Test
  void rejectsEmptyWorkYearsRangeAndUnknownScope() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setMinWorkYears(new BigDecimal("3"));
    request.setMaxWorkYears(new BigDecimal("3"));
    request.setScope("TEAM");

    assertThat(paths(validator.validate(request))).contains("workYearsRangeValid", "scope");
  }

  @Test
  void rejectsMalformedCandidateIds() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setCandidateIds(List.of(" ", "C".repeat(65)));

    assertThat(paths(validator.validate(request)))
        .allMatch(path -> path.startsWith("candidateIds"))
        .hasSize(2);
  }

  @Test
  void acceptsSearchInputAtConfiguredBoundaries() {
    TalentSearchRequest request = new TalentSearchRequest();
    request.setQ("Q".repeat(MAX_QUERY_LENGTH));
    request.setSkills(
        Collections.nCopies(MAX_FILTER_VALUES, "S".repeat(MAX_FILTER_VALUE_LENGTH)));
    request.setNaturalCondition("N".repeat(MAX_NATURAL_CONDITION_LENGTH));
    request.setCursor("C".repeat(MAX_CURSOR_LENGTH));
    request.setMinWorkYears(BigDecimal.valueOf(MAX_WORK_YEARS));
    request.setGender("OTHER");
    request.setMinAge(MIN_CANDIDATE_AGE);
    request.setMaxAge(MAX_CANDIDATE_AGE);
    request.setLimit(MAX_SEARCH_PAGE_SIZE);

    assertThat(validator.validate(request)).isEmpty();
  }

  private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}

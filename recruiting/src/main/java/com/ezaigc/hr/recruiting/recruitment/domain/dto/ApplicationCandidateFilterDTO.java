package com.ezaigc.hr.recruiting.recruitment.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Value;

@Value
public class ApplicationCandidateFilterDTO {
  String q;
  List<String> conditions;
  List<String> degrees;
  BigDecimal minWorkYears;
  BigDecimal maxWorkYears;
  Integer minAge;
  Integer maxAge;
  String gender;

  public ApplicationCandidateFilterDTO(String q, List<String> degrees, BigDecimal minWorkYears,
      BigDecimal maxWorkYears, Integer minAge, Integer maxAge, String gender) {
    this(q, List.of(), degrees, minWorkYears, maxWorkYears, minAge, maxAge, gender);
  }

  public ApplicationCandidateFilterDTO(String q, List<String> conditions, List<String> degrees,
      BigDecimal minWorkYears, BigDecimal maxWorkYears, Integer minAge, Integer maxAge, String gender) {
    this.q = q;
    this.conditions = List.copyOf(conditions);
    this.degrees = degrees;
    this.minWorkYears = minWorkYears;
    this.maxWorkYears = maxWorkYears;
    this.minAge = minAge;
    this.maxAge = maxAge;
    this.gender = gender;
  }

  public boolean hasFacts() {
    return !conditions.isEmpty() || !degrees.isEmpty() || minWorkYears != null || maxWorkYears != null
        || minAge != null || maxAge != null || gender != null;
  }
  public boolean isActive() { return q != null || hasFacts(); }
}

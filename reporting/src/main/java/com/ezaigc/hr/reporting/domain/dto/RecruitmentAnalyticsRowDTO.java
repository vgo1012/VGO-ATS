package com.ezaigc.hr.reporting.domain.dto;

import lombok.Data;

/** 数据库聚合行，不承载候选人明细。 */
@Data
public class RecruitmentAnalyticsRowDTO {
  private String dimension;
  private String id;
  private String name;
  private String date;
  private long resumes;
  private boolean inactive;
  private long applications;
  private long offers;
  private long onboarded;
  private long interview1;
  private long interview2;
  private long interview3;
  private long interviews;
  private long activeJobs;
  private long headcount;
  private long unknownHeadcountJobs;
  private long occupied;
  private long openHeadcount;
  private long completedCount;
  private Double averageTotalHours;
  private long activeCount;
  private Double averageActiveHours;
  private long passed;
  private long rejected;
  private long pending;
  private long withdrawn;
}

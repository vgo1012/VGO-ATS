package com.ezaigc.hr.talent.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRuntime;
import java.time.Instant;
import java.time.LocalDate;
public interface ResumeLifecycleRuntimePersistenceService extends IService<ResumeLifecycleRuntime> {
  int ensure(Instant now);
  int activate(Instant now);
  int claim(String owner,Instant now,Instant until);
  int complete(String owner,long generation,LocalDate date,Instant now);
  int release(String owner,Instant now);
  int calendarChanged();
}

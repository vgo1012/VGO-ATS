package com.ezaigc.hr.talent.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRuntime;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleRuntimeMapper;
import com.ezaigc.hr.talent.service.ResumeLifecycleRuntimePersistenceService;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
@Service
public class ResumeLifecycleRuntimePersistenceServiceImpl extends ServiceImpl<ResumeLifecycleRuntimeMapper,ResumeLifecycleRuntime>
    implements ResumeLifecycleRuntimePersistenceService {
  public int activate(Instant now) { return baseMapper.activate(now); }
  public int ensure(Instant now) { return baseMapper.ensure(now); }
  public int claim(String owner,Instant now,Instant until) { return baseMapper.claim(owner,now,until); }
  public int complete(String owner,long generation,LocalDate date,Instant now) { return baseMapper.complete(owner,generation,date,now); }
  public int release(String owner,Instant now) { return baseMapper.release(owner,now); }
  public int calendarChanged() { return baseMapper.calendarChanged(); }
}

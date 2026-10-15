package com.ezaigc.hr.talent.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycleRun;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleRunMapper;
import com.ezaigc.hr.talent.service.ResumeLifecycleRunPersistenceService;
import org.springframework.stereotype.Service;
@Service
public class ResumeLifecycleRunPersistenceServiceImpl extends ServiceImpl<ResumeLifecycleRunMapper,ResumeLifecycleRun>
    implements ResumeLifecycleRunPersistenceService {}

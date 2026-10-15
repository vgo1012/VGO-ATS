package com.ezaigc.hr.talent.service;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleReadyDTO;
import java.util.List;
public interface ResumeLifecyclePersistenceService extends IService<ResumeLifecycle> {
  ResumeLifecycle lockCurrent(String resumeVersionId);
  List<ResumeLifecycleReadyDTO> ready(String afterId, int limit);
  int lastCycle(String resumeVersionId);
}

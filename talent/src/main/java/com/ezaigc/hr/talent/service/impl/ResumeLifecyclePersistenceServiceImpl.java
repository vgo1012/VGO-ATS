package com.ezaigc.hr.talent.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.talent.domain.entity.ResumeLifecycle;
import com.ezaigc.hr.talent.domain.dto.ResumeLifecycleReadyDTO;
import com.ezaigc.hr.talent.mapper.ResumeLifecycleMapper;
import com.ezaigc.hr.talent.service.ResumeLifecyclePersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class ResumeLifecyclePersistenceServiceImpl extends ServiceImpl<ResumeLifecycleMapper, ResumeLifecycle>
    implements ResumeLifecyclePersistenceService {
  public ResumeLifecycle lockCurrent(String resumeVersionId) { return baseMapper.lockCurrent(resumeVersionId); }
  public List<ResumeLifecycleReadyDTO> ready(String afterId, int limit) { return baseMapper.ready(afterId,limit); }
  public int lastCycle(String resumeVersionId) { return baseMapper.lastCycle(resumeVersionId); }
}

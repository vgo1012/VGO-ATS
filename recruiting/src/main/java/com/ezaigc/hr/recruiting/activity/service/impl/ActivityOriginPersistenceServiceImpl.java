package com.ezaigc.hr.recruiting.activity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordRowDTO;
import com.ezaigc.hr.recruiting.activity.mapper.ActivityOriginMapper;
import com.ezaigc.hr.recruiting.activity.service.ActivityOriginPersistenceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ActivityOriginPersistenceServiceImpl extends ServiceImpl<ActivityOriginMapper, ActivityOriginEntity>
    implements ActivityOriginPersistenceService {
  public List<ActivityRecordRowDTO> findRecords(ActivityRecordQuery query) { return baseMapper.findRecords(query); }
}

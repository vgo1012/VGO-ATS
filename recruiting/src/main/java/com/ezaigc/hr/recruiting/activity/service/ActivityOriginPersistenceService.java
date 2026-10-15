package com.ezaigc.hr.recruiting.activity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.activity.domain.entity.ActivityOriginEntity;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordQuery;
import com.ezaigc.hr.recruiting.activity.domain.dto.ActivityRecordRowDTO;
import java.util.List;

public interface ActivityOriginPersistenceService extends IService<ActivityOriginEntity> {
  List<ActivityRecordRowDTO> findRecords(ActivityRecordQuery query);
}

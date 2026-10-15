package com.ezaigc.hr.recruiting.demand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.recruiting.demand.domain.entity.IdempotencyRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DemandIdempotencyMapper extends BaseMapper<IdempotencyRecordEntity> {}

package com.ezaigc.hr.talent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.talent.domain.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TalentIdempotencyMapper extends BaseMapper<IdempotencyRecord> {}

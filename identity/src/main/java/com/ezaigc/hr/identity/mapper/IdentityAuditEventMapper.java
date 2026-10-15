package com.ezaigc.hr.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ezaigc.hr.identity.domain.entity.IdentityAuditEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdentityAuditEventMapper extends BaseMapper<IdentityAuditEvent> {}

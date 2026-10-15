package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentEntity;
import com.ezaigc.hr.identity.mapper.WecomDepartmentMapper;
import com.ezaigc.hr.identity.service.WecomDepartmentPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WecomDepartmentPersistenceServiceImpl extends ServiceImpl<WecomDepartmentMapper, WecomDepartmentEntity>
        implements WecomDepartmentPersistenceService {}

package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomDepartmentLinkEntity;
import com.ezaigc.hr.identity.mapper.WecomDepartmentLinkMapper;
import com.ezaigc.hr.identity.service.WecomDepartmentLinkPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WecomDepartmentLinkPersistenceServiceImpl
        extends ServiceImpl<WecomDepartmentLinkMapper, WecomDepartmentLinkEntity>
        implements WecomDepartmentLinkPersistenceService {}

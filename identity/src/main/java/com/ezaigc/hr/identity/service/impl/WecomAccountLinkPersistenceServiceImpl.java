package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomAccountLinkEntity;
import com.ezaigc.hr.identity.mapper.WecomAccountLinkMapper;
import com.ezaigc.hr.identity.service.WecomAccountLinkPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WecomAccountLinkPersistenceServiceImpl
        extends ServiceImpl<WecomAccountLinkMapper, WecomAccountLinkEntity>
        implements WecomAccountLinkPersistenceService {}

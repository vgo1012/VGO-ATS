package com.ezaigc.hr.identity.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.identity.domain.entity.WecomMemberEntity;
import com.ezaigc.hr.identity.mapper.WecomMemberMapper;
import com.ezaigc.hr.identity.service.WecomMemberPersistenceService;
import org.springframework.stereotype.Service;

@Service
public class WecomMemberPersistenceServiceImpl extends ServiceImpl<WecomMemberMapper, WecomMemberEntity>
        implements WecomMemberPersistenceService {}

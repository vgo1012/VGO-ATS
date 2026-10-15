package com.ezaigc.hr.recruiting.submission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.recruiting.submission.domain.entity.ClientRestrictionCurrent;
import com.ezaigc.hr.recruiting.submission.mapper.ClientRestrictionCurrentMapper;
import com.ezaigc.hr.recruiting.submission.service.ClientRestrictionCurrentPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ClientRestrictionCurrent 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ClientRestrictionCurrentPersistenceServiceImpl
    extends ServiceImpl<ClientRestrictionCurrentMapper, ClientRestrictionCurrent>
    implements ClientRestrictionCurrentPersistenceService {

  @Override
  public int hasRestriction(String clientId, String candidateId, Instant now) {
    return Math.toIntExact(
        baseMapper.selectCount(
            Wrappers.lambdaQuery(ClientRestrictionCurrent.class)
                .eq(ClientRestrictionCurrent::getClientId, clientId)
                .eq(ClientRestrictionCurrent::getCandidateId, candidateId)
                .eq(ClientRestrictionCurrent::isActive, true)
                .and(
                    restriction ->
                        restriction
                            .isNull(ClientRestrictionCurrent::getExpiresAt)
                            .or()
                            .gt(ClientRestrictionCurrent::getExpiresAt, now))));
  }
}

package com.ezaigc.hr.intelligence.search.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.intelligence.search.domain.entity.SearchIndexResource;
import com.ezaigc.hr.intelligence.search.mapper.SearchIndexResourceMapper;
import com.ezaigc.hr.intelligence.search.service.SearchIndexResourceService;
import java.util.List;
import com.ezaigc.hr.intelligence.search.enums.SearchIndexResourceStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchIndexResourceServiceImpl extends ServiceImpl<SearchIndexResourceMapper, SearchIndexResource>
    implements SearchIndexResourceService {
  @Override public SearchIndexResource find(String name) {
    return getOne(Wrappers.<SearchIndexResource>lambdaQuery().eq(SearchIndexResource::getIndexName, name));
  }
  @Override public SearchIndexResource lock(String name) { return baseMapper.forUpdate(name); }
  @Override public long liveCount() { return baseMapper.liveCount(SearchIndexResourceStatus.DELETED.getCode()).size(); }
  @Override public long unresolvedCount() { return baseMapper.unresolvedCount(SearchIndexResourceStatus.CREATING.getCode(), SearchIndexResourceStatus.CREATE_UNKNOWN.getCode()).size(); }
  @Override public boolean pin(String id, long micros) { return baseMapper.pin(id, micros, SearchIndexResourceStatus.READY.getCode()) == 1; }
  @Override public boolean retiring(String id, long version, String actor) {
    return baseMapper.retiring(id, version, actor, SearchIndexResourceStatus.READY.getCode(), SearchIndexResourceStatus.RETIRING.getCode()) == 1;
  }
  @Override public List<SearchIndexResource> candidates(long age, int limit) { return baseMapper.candidates(age, limit, SearchIndexResourceStatus.READY.getCode(), SearchIndexResourceStatus.RETIRING.getCode()); }
  @Override public List<String> newest(int limit) { return baseMapper.newest(limit, SearchIndexResourceStatus.READY.getCode()); }
}

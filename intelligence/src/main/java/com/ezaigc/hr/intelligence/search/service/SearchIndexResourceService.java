package com.ezaigc.hr.intelligence.search.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.search.domain.entity.SearchIndexResource;
import java.util.List;

public interface SearchIndexResourceService extends IService<SearchIndexResource> {
  SearchIndexResource find(String indexName);
  SearchIndexResource lock(String indexName);
  long liveCount();
  long unresolvedCount();
  boolean pin(String id, long micros);
  boolean retiring(String id, long version, String actor);
  List<SearchIndexResource> candidates(long ageSeconds, int limit);
  List<String> newest(int limit);
}

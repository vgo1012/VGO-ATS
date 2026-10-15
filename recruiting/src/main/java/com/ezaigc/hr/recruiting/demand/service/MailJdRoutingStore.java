package com.ezaigc.hr.recruiting.demand.service;

import com.ezaigc.hr.recruiting.demand.domain.entity.MailJdRoutingRow;
import java.util.List;

public interface MailJdRoutingStore {
  List<MailJdRoutingRow> findActiveTargets(String publishedStatus, String activeStatus);
  MailJdRoutingRow findCurrentTargetForShare(String jdId, String publishedStatus, String activeStatus);
}

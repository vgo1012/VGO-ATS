package com.ezaigc.hr.integration.mail.domain;

import java.util.List;
import lombok.Value;

@Value
public class MailJdOptionResponse {
  String id;
  String code;
  String title;
  int currentVersionNo;
  List<String> regions;
}

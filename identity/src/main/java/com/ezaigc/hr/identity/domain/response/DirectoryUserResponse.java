package com.ezaigc.hr.identity.domain.response;

import lombok.Value;

/** 招聘业务中的人员引用，不包含账号联系方式、角色或凭据。 */
@Value
public class DirectoryUserResponse {
  String id;
  String displayName;
  String homeDepartmentId;
  String status;
}

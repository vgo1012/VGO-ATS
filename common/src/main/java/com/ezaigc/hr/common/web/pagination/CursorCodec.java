package com.ezaigc.hr.common.web.pagination;

import java.util.List;

/** 对不可信客户端隐藏并校验分页位置。 */
public interface CursorCodec {

  String encode(String scope, List<String> values);

  List<String> decode(String token, String expectedScope, int expectedValueCount);
}

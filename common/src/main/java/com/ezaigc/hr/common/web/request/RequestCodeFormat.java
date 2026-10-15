package com.ezaigc.hr.common.web.request;

/** HTTP 请求中受控业务编码的通用安全字符契约。 */
public final class RequestCodeFormat {

  /** 允许大小写字母、数字和常用代码分隔符；仅兼容普通首尾空格，拒绝控制字符。 */
  public static final String INPUT_PATTERN = "^ *[A-Za-z0-9][A-Za-z0-9_.:-]* *$";

  private RequestCodeFormat() {}
}

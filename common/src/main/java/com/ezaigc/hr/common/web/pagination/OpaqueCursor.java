package com.ezaigc.hr.common.web.pagination;

import java.util.regex.Pattern;

public final class OpaqueCursor {

  public static final int MAX_CURSOR_LENGTH = 2048;
  public static final String CURSOR_INPUT_PATTERN = "[A-Za-z0-9_-]{1,2048}";

  private static final Pattern FORMAT = Pattern.compile(CURSOR_INPUT_PATTERN);

  private OpaqueCursor() {}

  public static String requireValid(String value) {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Cursor is not a valid base64url token");
    }
    return value;
  }
}

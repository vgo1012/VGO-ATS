package com.ezaigc.hr.talent.api;

import java.io.IOException;
import java.io.InputStream;

/** 对象存储模块能力端口。 */
public interface ObjectStoragePort {
  void put(String objectKey, InputStream content) throws IOException;

  InputStream open(String objectKey) throws IOException;

  boolean exists(String objectKey) throws IOException;

  /** 删除指定业务记录。 */
  void delete(String objectKey) throws IOException;
}

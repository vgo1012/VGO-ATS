package com.ezaigc.hr.recruiting.demand.application.support;

import com.ezaigc.hr.common.web.error.PlatformExceptions;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** JD 工作地区只接受目录中的单个城市；历史简称保持原代码并规范展示。 */
public final class JdCityNames {
  private static final Map<String, String> NAMES = load();

  private JdCityNames() {}

  public static String requireCity(String value) {
    String city = value == null ? null : NAMES.get(value.strip());
    if (city == null) {
      throw PlatformExceptions.unprocessable("JD_WORK_REGION_INVALID", "工作地区必须选择一个有效城市，不支持区县、驻场或多个城市");
    }
    return city;
  }

  public static String display(String value) {
    return value == null ? null : NAMES.getOrDefault(value.strip(), value);
  }

  public static String searchQuery(String value) {
    String city = value == null ? null : NAMES.get(value);
    return city == null ? value : shortName(city);
  }

  public static String shortName(String city) {
    return city.substring(0, city.length() - 1);
  }

  private static Map<String, String> load() {
    try (var stream = JdCityNames.class.getResourceAsStream("/jd-cities-2023.json")) {
      if (stream == null) throw new IllegalStateException("JD 城市目录缺失");
      Map<String, String> names = new LinkedHashMap<>();
      for (String[] row : new ObjectMapper().readValue(stream, String[][].class)) {
        String city = row[1];
        names.put(city, city);
        names.put(shortName(city), city);
      }
      return Map.copyOf(names);
    } catch (IOException failure) {
      throw new IllegalStateException("JD 城市目录读取失败", failure);
    }
  }
}

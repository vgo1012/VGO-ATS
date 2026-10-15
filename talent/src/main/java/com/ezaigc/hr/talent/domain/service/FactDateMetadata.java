package com.ezaigc.hr.talent.domain.service;

import com.ezaigc.hr.common.infrastructure.json.JsonText;
import com.ezaigc.hr.common.infrastructure.persistence.StandardPersistenceFields;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import tools.jackson.databind.ObjectMapper;

/** 使用现有事实扩展列保存年/月/至今，不向 DATE 列补造日期。 */
public final class FactDateMetadata {
    private static final ObjectMapper JSON = new ObjectMapper();
    private FactDateMetadata() {}
    public static <T extends StandardPersistenceFields> T capture(T row, String start, String end) {
        var dates = new LinkedHashMap<String, String>();
        dates.put("startDateText", start);
        dates.put("endDateText", end);
        row.setExt(JsonText.write(dates));
        return row;
    }
    public static String read(String ext, String key, LocalDate completeDate) {
        if (ext != null && !ext.isBlank()) {
            var value = JSON.readTree(ext).get(key);
            if (value != null && value.isString()) return value.asString();
        }
        return completeDate == null ? null : completeDate.toString();
    }
}

package com.ezaigc.hr.common.web.pagination;

import com.ezaigc.hr.common.web.error.PlatformException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StableCursor {

  /** 排序时间。 */
  private Instant sortTime;
  /** 主键 ID。 */
  private String id;

  public String encode(CursorCodec codec, String scope) {
    return codec.encode(scope, List.of(sortTime.toString(), id));
  }

  public static StableCursor decode(CursorCodec codec, String scope, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      List<String> parts = codec.decode(value, scope, 2);
      if (parts.get(1).length() != 26) {
        throw new IllegalArgumentException("invalid cursor payload");
      }
      return new StableCursor(Instant.parse(parts.get(0)), parts.get(1));
    } catch (IllegalArgumentException | DateTimeParseException exception) {
      throw new PlatformException(HttpStatus.BAD_REQUEST, "CURSOR_INVALID", "分页游标无效", true);
    }
  }
}

package com.ezaigc.hr.recruiting.demand.domain.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

/** 仅接受 JSON 整数，避免浮点业务值被 Jackson 截断。 */
public class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
      throw InvalidFormatException.from(parser, "必须是 JSON 整数", parser.getValueAsString(), Integer.class);
    }
    return parser.getIntValue();
  }
}

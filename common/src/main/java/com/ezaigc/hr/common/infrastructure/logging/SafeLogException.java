package com.ezaigc.hr.common.infrastructure.logging;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;

/**
 * 仅用于日志的异常副本：保留异常类型、调用栈和经白名单验证的结构化诊断字段，
 * 不复制可能包含业务正文、SQL 参数或凭据的原始消息。
 *
 * <p>原异常仍用于业务分类和重试判断。异常图最多复制32个节点，每节点最多64帧，防止循环或过大异常图淹没日志。
 */
public final class SafeLogException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private static final int MAX_NODES = 32;
  private static final int MAX_FRAMES = 64;
  private static final Pattern SQL_STATE = Pattern.compile("[A-Z0-9]{5}");

  private SafeLogException(Throwable failure) {
    super(description(failure));
  }

  public static SafeLogException from(Throwable failure) {
    Objects.requireNonNull(failure, "failure");
    return copy(failure, new CopyBudget());
  }

  private static SafeLogException copy(Throwable failure, CopyBudget budget) {
    if (failure == null || budget.remaining == 0 || !budget.seen.add(failure)) {
      return null;
    }
    budget.remaining--;
    SafeLogException safe = new SafeLogException(failure);
    StackTraceElement[] trace = failure.getStackTrace();
    safe.setStackTrace(Arrays.copyOf(trace, Math.min(trace.length, MAX_FRAMES)));
    SafeLogException cause = copy(failure.getCause(), budget);
    if (cause != null) {
      safe.initCause(cause);
    }
    for (Throwable suppressed : failure.getSuppressed()) {
      if (budget.remaining == 0) {
        break;
      }
      SafeLogException safeSuppressed = copy(suppressed, budget);
      if (safeSuppressed != null) {
        safe.addSuppressed(safeSuppressed);
      }
    }
    if (failure instanceof SQLException sqlFailure) {
      SQLException next = sqlFailure.getNextException();
      while (next != null && budget.remaining > 0) {
        if (budget.seen.contains(next)) {
          break;
        }
        SafeLogException safeNext = copy(next, budget);
        if (safeNext != null) {
          safe.addSuppressed(safeNext);
        }
        next = next.getNextException();
      }
    }
    return safe;
  }

  private static String description(Throwable failure) {
    String exceptionType = failure.getClass().getName();
    if (failure instanceof JacksonException jsonFailure && !jsonFailure.getPath().isEmpty()) {
      return exceptionType + " [jsonPath=" + jsonPath(jsonFailure) + "]";
    }
    if (!(failure instanceof SQLException sqlFailure)) {
      return exceptionType;
    }
    StringBuilder description = new StringBuilder(exceptionType).append(" [");
    String sqlState = sqlFailure.getSQLState();
    if (sqlState != null && SQL_STATE.matcher(sqlState).matches()) {
      description.append("sqlState=").append(sqlState).append(", ");
    }
    return description.append("errorCode=").append(sqlFailure.getErrorCode())
        .append(", category=").append(sqlCategory(sqlFailure)).append(']').toString();
  }

  /** 只输出由 Java 类型声明验证的属性名和数组下标，不输出动态键、对象值或原始异常消息。 */
  private static String jsonPath(JacksonException failure) {
    StringBuilder path = new StringBuilder("$");
    int count = 0;
    for (var reference : failure.getPath()) {
      if (count++ == 16) {
        path.append("...");
        break;
      }
      String property = reference.getPropertyName();
      if (declaredProperty(reference.from(), property)) {
        path.append('.').append(property);
      } else if (property == null && reference.getIndex() >= 0) {
        path.append('[').append(reference.getIndex()).append(']');
      } else {
        path.append("[?]");
      }
    }
    return path.toString();
  }

  private static boolean declaredProperty(Object source, String property) {
    if (source == null || property == null || property.isEmpty() || property.length() > 80) return false;
    Class<?> type = source instanceof Class<?> sourceType ? sourceType : source.getClass();
    if (Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) return false;
    String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
    // 仅检查元数据，不调用 getter；别名和动态属性无法验证时按未知字段处理。
    for (var method : type.getMethods()) {
      if (method.getParameterCount() == 0 && (method.getName().equals("get" + suffix)
          || method.getName().equals("is" + suffix)
          || (type.isRecord() && method.getName().equals(property)))) return true;
    }
    for (var field : type.getFields()) {
      if (field.getName().equals(property)) return true;
    }
    return false;
  }

  private static String sqlCategory(SQLException failure) {
    String vendorCategory = switch (failure.getErrorCode()) {
      case 1042 -> "connection-failure";
      case 1045 -> "authentication-failure";
      case 1049 -> "database-not-found";
      case 1054 -> "column-not-found";
      case 1062 -> "duplicate-key";
      case 1146 -> "table-not-found";
      case 1205 -> "lock-timeout";
      case 1213 -> "deadlock";
      case 1364 -> "required-column-missing";
      case 1366 -> "invalid-column-value";
      case 1406 -> "column-value-too-long";
      case 1451 -> "foreign-key-delete-conflict";
      case 1452 -> "foreign-key-write-conflict";
      case 3819 -> "check-constraint-violation";
      default -> null;
    };
    if (vendorCategory != null) {
      return vendorCategory;
    }
    String sqlState = failure.getSQLState();
    if (sqlState == null || !SQL_STATE.matcher(sqlState).matches()) {
      return "database-error";
    }
    return switch (sqlState.substring(0, 2)) {
      case "08" -> "connection-failure";
      case "22" -> "invalid-data";
      case "23" -> "integrity-constraint-violation";
      case "28" -> "authorization-failure";
      case "40" -> "transaction-rollback";
      case "42" -> "sql-syntax-or-access-rule";
      default -> "database-error";
    };
  }

  private static final class CopyBudget {
    private int remaining = MAX_NODES;
    private final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
  }
}

package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.ezaigc.hr.intelligence.matching.mapper.MatchingConfigurationMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.jupiter.api.Test;
import java.io.File;

class SchemaEntityAlignmentContractTest {

  private static final Pattern LEGACY_AUDIT_VOCABULARY =
      Pattern.compile(
          "(?i)(?<![a-z0-9_])(?:created_by_user_id|created_at|updated_by_user_id|updated_at|created_by|updated_by)(?![a-z0-9_])"
              + "|(?<![A-Za-z0-9_])(?:createdByUserId|createdAt|updatedByUserId|updatedAt|createdBy|updatedBy)(?![A-Za-z0-9_])"
              + "|\\b(?:get|set)(?:CreatedByUserId|CreatedAt|UpdatedByUserId|UpdatedAt|CreatedBy|UpdatedBy)\\b");

  private static final Pattern TABLE_START =
      Pattern.compile("(?im)^\\s*create\\s+table\\s+`?([a-z0-9_]+)`?\\s*\\(");
  private static final Pattern COLUMN_DEFINITION =
      Pattern.compile(
          "^\\s*`?([a-z][a-z0-9_]*)`?\\s+"
              + "(?:char|varchar|text|mediumtext|longtext|json|boolean|tinyint|smallint|int|bigint|decimal|date|datetime|timestamp|blob|longblob|binary|varbinary|double|float)\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern PRIMARY_KEY_DEFINITION =
      Pattern.compile("(?is)\\bprimary\\s+key\\s*\\(([^)]*)\\)");
  private static final Pattern KEY_COLUMN = Pattern.compile("`?([a-z][a-z0-9_]*)`?");
  private static final Set<String> DEFINITION_KEYWORDS =
      Set.of("primary", "unique", "index", "key", "constraint", "check", "foreign");
  private static final Set<String> INTENTIONAL_SQL_ONLY_TABLES =
      Set.of(
          // 单例计数器只通过标量 SQL 原子递增/读取，无需暴露可整行修改的实体。
          "demand_jd_code_sequence",
          "demand_jd_code_pair_sequence",
          "demand_jd_department_code",
          "identity_permission",
          "identity_role_permission",
          "interview_template",
          "interview_template_version",
          // 匹配结果反馈入口已退役；仅保留历史数据，不再映射应用实体或提供读写接口。
          "matching_ai_result_feedback",
          "matching_ownership_history",
          "platform_idempotency_record",
          "recruitment_application_collaborator_change_record",
          "reporting_export_download_audit",
          "reporting_export_file",
          "reporting_export_job",
          "reporting_export_object",
          "reporting_metric_definition",
          "reporting_metric_version",
          "reporting_user_overdue_threshold_version",
          "reporting_work_target",
          // 筛选模板功能已退役；仅保留历史数据，不再映射应用实体或提供读写接口。
          "search_filter_template",
          "search_filter_template_version",
          "search_filter_template_share",
          "spring_session",
          "spring_session_attributes",
          "talent_fact_correction",
          "talent_mail_ingest_run",
          "talent_mail_resume",
          "talent_mail_route_rule",
          "talent_mailbox_cursor");

  private final JavaClasses productionClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.ezaigc.hr");

  @Test
  void mappedEntityColumnsExactlyMatchTheConsolidatedSchema() throws IOException {
    Map<String, Set<String>> schema = readSchema();
    Map<String, Class<?>> mappedEntities = new LinkedHashMap<>();
    List<String> violations = new ArrayList<>();

    productionClasses.stream()
        .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
        .forEach(
            javaClass -> {
              Class<?> entityType = javaClass.reflect();
              String table =
                  entityType.getAnnotation(TableName.class).value().toLowerCase(Locale.ROOT);
              Class<?> previous = mappedEntities.put(table, entityType);
              if (previous != null) {
                violations.add(
                    table + " -> 同时映射 " + previous.getName() + " 和 " + entityType.getName());
                return;
              }

              Set<String> expectedColumns = schema.get(table);
              if (expectedColumns == null) {
                violations.add(entityType.getName() + " -> schema 中不存在表 " + table);
                return;
              }

              Map<String, String> mappedColumns = mappedColumns(entityType, violations);
              Set<String> missing = new LinkedHashSet<>(expectedColumns);
              missing.removeAll(mappedColumns.keySet());
              Set<String> extra = new LinkedHashSet<>(mappedColumns.keySet());
              extra.removeAll(expectedColumns);
              if (!missing.isEmpty() || !extra.isEmpty()) {
                violations.add(
                    table
                        + " -> 缺少字段 "
                        + missing
                        + "，多余字段 "
                        + extra
                        + " ("
                        + entityType.getName()
                        + ")");
              }
            });

    assertTrue(violations.isEmpty(), () -> "实体与建表字段不一致:\n" + String.join("\n", violations));

    Set<String> sqlOnlyTables = new HashSet<>(schema.keySet());
    sqlOnlyTables.removeAll(mappedEntities.keySet());
    assertEquals(INTENTIONAL_SQL_ONLY_TABLES, sqlOnlyTables, "未映射实体的表只能是显式 SQL 投影、框架托管表、平台基础表或明确保留的退役数据表");
  }

  @Test
  void tableIdMetadataMatchesTheSchemaPrimaryKeyShape() throws IOException {
    Map<String, List<String>> primaryKeys = readPrimaryKeys();
    List<String> violations = new ArrayList<>();

    productionClasses.stream()
        .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
        .map(javaClass -> javaClass.reflect())
        .forEach(
            entityType -> {
              String table =
                  entityType.getAnnotation(TableName.class).value().toLowerCase(Locale.ROOT);
              List<String> schemaPrimaryKey = primaryKeys.getOrDefault(table, List.of());
              List<String> mappedTableIds = tableIdColumns(entityType);
              if (schemaPrimaryKey.size() == 1 && !mappedTableIds.equals(schemaPrimaryKey)) {
                violations.add(
                    table
                        + " -> 单列主键必须由同名 @TableId 映射，schema="
                        + schemaPrimaryKey
                        + ", entity="
                        + mappedTableIds);
              } else if (schemaPrimaryKey.size() > 1 && !mappedTableIds.isEmpty()) {
                violations.add(
                    table
                        + " -> MyBatis-Plus 不支持复合 @TableId，不得把其中一列伪装为完整主键: "
                        + mappedTableIds);
              }
            });

    assertTrue(
        violations.isEmpty(),
        () -> "实体主键元数据必须与建表脚本一致:\n" + String.join("\n", violations));
  }

  @Test
  void entityReturningSelectsIncludeEveryMappedColumn() {
    List<String> violations = new ArrayList<>();
    productionClasses.stream()
        .map(javaClass -> javaClass.reflect())
        .forEach(
            mapperType -> {
              for (Method method : mapperType.getDeclaredMethods()) {
                Select select = method.getAnnotation(Select.class);
                Class<?> entityType = entityType(method.getGenericReturnType());
                if (select == null || entityType == null) {
                  continue;
                }
                Matcher selectListMatcher =
                    Pattern.compile("(?is)\\bselect\\s+(?:distinct\\s+)?(.*?)\\s+from\\b")
                        .matcher(String.join(" ", select.value()));
                if (!selectListMatcher.find()) {
                  violations.add(method.toGenericString() + " -> 无法解析 SELECT 列表");
                  continue;
                }
                String selectList = selectListMatcher.group(1);
                Set<String> missing = new LinkedHashSet<>();
                for (String column : mappedColumns(entityType, violations).keySet()) {
                  if (!Pattern.compile(
                          "(?i)(?<![a-z0-9_])" + Pattern.quote(column) + "(?![a-z0-9_])")
                      .matcher(selectList)
                      .find()) {
                    missing.add(column);
                  }
                }
                if (!missing.isEmpty()) {
                  violations.add(method.toGenericString() + " -> SELECT 缺少字段 " + missing);
                }
              }
            });

    assertTrue(
        violations.isEmpty(),
        () -> "返回持久化实体的显式 SELECT 必须覆盖全部映射字段:\n" + String.join("\n", violations));
  }

  @Test
  void weightProfileDeletionGuardIncludesEveryDirectForeignKeyConsumer() throws Exception {
    String schema = Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
    Matcher tables = Pattern.compile("(?is)CREATE TABLE\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*ENGINE=InnoDB")
        .matcher(schema);
    Pattern reference = Pattern.compile("(?i)REFERENCES\\s+`?matching_weight_profile_version`?\\s*\\(");
    Set<String> consumers = new HashSet<>();
    while (tables.find()) {
      if (reference.matcher(tables.group(2)).find()) {
        consumers.add(tables.group(1).toLowerCase(Locale.ROOT));
      }
    }
    assertFalse(consumers.isEmpty(), "必须从部署基线取得权重版本引用表，避免空集合误通过");
    var method = MatchingConfigurationMapper.class.getMethod("countWeightProfileReferences", String.class);
    String sql = String.join(" ", method.getAnnotation(Select.class).value());
    var bound = new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Map.class)
        .getBoundSql(Map.of("profileId", "owned-profile"));
    Set<String> queriedTables = TablesNamesFinder.findTables(bound.getSql());
    assertTrue(queriedTables.containsAll(consumers),
        () -> "删除保护必须覆盖全部直接外键引用: " + consumers + "，当前查询: " + queriedTables);
    assertFalse(bound.getParameterMappings().isEmpty(), "引用检查必须限定当前模板");
    assertTrue(bound.getParameterMappings().stream().allMatch(parameter -> parameter.getProperty().equals("profileId")),
        "每项引用查询只绑定当前模板，不能依赖运行状态等条件忽略历史引用");
  }

  @Test
  void productionCodeUsesOnlyTheUnifiedAuditVocabulary() throws IOException {
    Path root = repositoryRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = JavaImportPolicy.repositorySources(root).keySet().stream()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> path.toString().contains("src" + File.separator + "main"))
          .filter(path -> !path.startsWith(root.resolve(".planning")))
          .filter(path -> !path.startsWith(root.resolve(".git")))
          .filter(
              path ->
                  !path.toString()
                      .contains(File.separator + "target" + File.separator))
          .forEach(
              path -> {
                try {
                  if (LEGACY_AUDIT_VOCABULARY.matcher(Files.readString(path)).find()) {
                    violations.add(root.relativize(path).toString());
                  }
                } catch (IOException exception) {
                  throw new IllegalStateException("读取源码失败: " + path, exception);
                }
              });
    }
    assertTrue(
        violations.isEmpty(), () -> "生产代码不得保留与统一字段重复的旧审计词汇:\n" + String.join("\n", violations));
  }

  private static Class<?> entityType(Type returnType) {
    if (returnType instanceof Class<?> candidate) {
      return candidate.isAnnotationPresent(TableName.class) ? candidate : null;
    }
    if (returnType instanceof ParameterizedType parameterizedType) {
      for (Type argument : parameterizedType.getActualTypeArguments()) {
        Class<?> candidate = entityType(argument);
        if (candidate != null) {
          return candidate;
        }
      }
    }
    return null;
  }

  private static Map<String, String> mappedColumns(Class<?> entityType, List<String> violations) {
    Map<String, String> columns = new LinkedHashMap<>();
    for (Class<?> current = entityType;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        TableField tableField = field.getAnnotation(TableField.class);
        if (tableField != null && !tableField.exist()) {
          continue;
        }
        TableId tableId = field.getAnnotation(TableId.class);
        String explicitColumn =
            tableField != null && !tableField.value().isBlank()
                ? tableField.value()
                : tableId != null && !tableId.value().isBlank() ? tableId.value() : null;
        if (explicitColumn == null) {
          violations.add(
              entityType.getName()
                  + "."
                  + field.getName()
                  + " -> 持久化字段必须通过 @TableField 或 @TableId 显式声明数据库列名");
        }
        String column =
            explicitColumn == null
                ? camelToSnake(field.getName())
                : explicitColumn.toLowerCase(Locale.ROOT);
        String previous = columns.put(column, current.getName() + "." + field.getName());
        if (previous != null) {
          violations.add(
              entityType.getName()
                  + " -> 字段 "
                  + column
                  + " 被重复映射: "
                  + previous
                  + " / "
                  + columns.get(column));
        }
      }
    }
    return columns;
  }

  private static List<String> tableIdColumns(Class<?> entityType) {
    List<String> columns = new ArrayList<>();
    for (Class<?> current = entityType;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        TableId tableId = field.getAnnotation(TableId.class);
        if (tableId != null) {
          columns.add(
              tableId.value().isBlank()
                  ? camelToSnake(field.getName())
                  : tableId.value().toLowerCase(Locale.ROOT));
        }
      }
    }
    return columns;
  }

  private static String camelToSnake(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static Map<String, Set<String>> readSchema() throws IOException {
    String sql = Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
    Map<String, Set<String>> tables = new LinkedHashMap<>();
    List<String> tableNames = new ArrayList<>();
    List<Integer> bodyStarts = new ArrayList<>();
    List<Integer> statementStarts = new ArrayList<>();
    Matcher tableMatcher = TABLE_START.matcher(sql);
    while (tableMatcher.find()) {
      tableNames.add(tableMatcher.group(1).toLowerCase(Locale.ROOT));
      bodyStarts.add(tableMatcher.end());
      statementStarts.add(tableMatcher.start());
    }
    for (int index = 0; index < tableNames.size(); index++) {
      String table = tableNames.get(index);
      int bodyEnd =
          index + 1 < statementStarts.size() ? statementStarts.get(index + 1) : sql.length();
      Set<String> columns = new LinkedHashSet<>();
      for (String line : sql.substring(bodyStarts.get(index), bodyEnd).split("\\R")) {
        if (line.stripLeading().startsWith(")")) {
          break;
        }
        Matcher columnMatcher = COLUMN_DEFINITION.matcher(line);
        if (!columnMatcher.find()) {
          continue;
        }
        String column = columnMatcher.group(1).toLowerCase(Locale.ROOT);
        if (!DEFINITION_KEYWORDS.contains(column)) {
          columns.add(column);
        }
      }
      tables.put(table, columns);
    }
    assertEquals(177, tables.size(), "实体对账必须读取完整的 177 张表（含 JD 岗位名称建议、部门码、编号字典、组合序列、入职流程、收件 Outbox 及搜索资源账本）");
    assertTrue(tables.containsKey("search_index_resource"), "搜索创建、请求保护和回收必须有持久资源账本");
    assertTrue(tables.containsKey("talent_resume_jd_score_binding"), "实体对账必须包含显式重评绑定表");
    assertTrue(tables.containsKey("matching_ai_professional_assessment"), "实体对账必须包含 AI 专业评分表");
    assertTrue(tables.containsKey("demand_jd_source"), "JD 来源必须独立保存来源及收件归属");
    return tables;
  }

  private static Map<String, List<String>> readPrimaryKeys() throws IOException {
    String sql = Files.readString(repositoryRoot().resolve("docs/sql/hr_ai_complete_schema.sql"));
    Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
    List<String> tableNames = new ArrayList<>();
    List<Integer> bodyStarts = new ArrayList<>();
    List<Integer> statementStarts = new ArrayList<>();
    Matcher tableMatcher = TABLE_START.matcher(sql);
    while (tableMatcher.find()) {
      tableNames.add(tableMatcher.group(1).toLowerCase(Locale.ROOT));
      bodyStarts.add(tableMatcher.end());
      statementStarts.add(tableMatcher.start());
    }
    for (int index = 0; index < tableNames.size(); index++) {
      int bodyEnd =
          index + 1 < statementStarts.size() ? statementStarts.get(index + 1) : sql.length();
      Matcher primaryKeyMatcher =
          PRIMARY_KEY_DEFINITION.matcher(sql.substring(bodyStarts.get(index), bodyEnd));
      if (!primaryKeyMatcher.find()) {
        primaryKeys.put(tableNames.get(index), List.of());
        continue;
      }
      List<String> columns = new ArrayList<>();
      Matcher columnMatcher = KEY_COLUMN.matcher(primaryKeyMatcher.group(1));
      while (columnMatcher.find()) {
        columns.add(columnMatcher.group(1).toLowerCase(Locale.ROOT));
      }
      primaryKeys.put(tableNames.get(index), List.copyOf(columns));
    }
    return primaryKeys;
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("pom.xml"))
          && Files.isDirectory(current.resolve("docs/sql"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("无法定位仓库根目录");
  }
}

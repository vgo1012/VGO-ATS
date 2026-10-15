package com.ezaigc.hr.talent.service.validator;

import com.ezaigc.hr.common.infrastructure.ai.ModelOutputGuard;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.talent.api.ResumeDateValue;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Credential;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Education;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Language;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ProjectExperience;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.Skill;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.WorkExperience;
import com.ezaigc.hr.talent.constant.TalentConstants;
import com.ezaigc.hr.talent.enums.CandidateGender;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 简历解析契约业务校验器，负责验证输入和领域约束。
 *
 * Validates model output before it is allowed to become an immutable fact snapshot.
 */
@Component
public class ResumeParseContractValidator {

  @Resource private ObjectMapper objectMapper;
  @Resource private Clock clock;

  private static final Set<String> TOP_LEVEL =
      /** 根据给定值创建当前对象。 */
      Set.of(
          "candidate",
          "workExperiences",
          "projectExperiences",
          "educations",
          "skills",
          "credentials",
          "languages",
          "industryTags",
          "responsibilityTags");
  private static final Set<String> LEGACY_TOP_LEVEL = Set.of(
      "candidate", "workExperiences", "projectExperiences", "educations", "skills",
      "credentials", "languages", "industryTags", "responsibilityTags", "unknownItems");
  private static final Set<String> DIAGNOSTIC_FIELDS = Set.of(
      "candidate", "fullName", "email", "phone", "currentRole", "city", "birthDate",
      "age", "gender", "jobIntention", "workExperiences", "projectExperiences",
      "educations", "skills", "credentials", "languages", "industryTags",
      "responsibilityTags", "unknownItems", "id", "employer", "role", "startDate",
      "endDate", "description", "responsibilities", "achievements", "projectName",
      "technologies", "school", "degree", "major", "learningMode", "name", "level",
      "years", "issuer", "issuedDate", "expiryDate");
  private static final Pattern ITEM_ID =
      Pattern.compile(TalentConstants.RESUME_ITEM_ID_INPUT_PATTERN);
  private static final Pattern EMAIL =
      Pattern.compile(TalentConstants.CANDIDATE_EMAIL_INPUT_PATTERN);
  private static final Pattern PHONE =
      Pattern.compile(TalentConstants.CANDIDATE_PHONE_INPUT_PATTERN);
  private static final Pattern YEAR_RANGE = Pattern.compile("^([0-9]{4})-([0-9]{4})$");
  private static final Pattern EXPLICIT_HEADER_AGE = Pattern.compile(
      "(?<![\\p{L}\\p{N}])年龄\\s*[:：]\\s*([0-9]{1,3})(?:\\s*周?岁)?+"
          + "(?![-0-9+＋/／－—–~～]|\\s*(?:至|到|以上|以下|以内|左右))"
          + "(?=$|[\\s,，;；|｜)）\\]】])");
  private static final List<String> AGE_REQUIREMENT_HEADINGS = List.of(
      "岗位要求", "任职要求", "招聘要求", "职位要求", "招聘条件", "任职资格", "岗位条件", "年龄要求");
  private static final List<String> OTHER_PERSON_LABELS = List.of(
      "推荐人", "联系人", "父亲", "母亲", "配偶", "家属", "亲属");
  private static final List<String> PERSONAL_HEADER_LABELS = List.of(
      "个人信息", "基本信息", "姓名", "电话", "手机", "邮箱", "电子邮件", "性别", "出生");
  private static final int AGE_HEADER_LIMIT = 500;

  public ResumeFacts validate(String rawJson, List<PageText> pages) {
    return validate(rawJson, pages, false, LocalDate.now(clock));
  }

  public ResumeFacts validateModel(String rawJson, List<PageText> pages) {
    return validateModel(rawJson, pages, LocalDate.now(clock));
  }

  public ResumeFacts validateModel(String rawJson, List<PageText> pages, LocalDate asOf) {
    return validate(rawJson, pages, true, asOf);
  }

  private ResumeFacts validate(String rawJson, List<PageText> pages, boolean modelOutput, LocalDate asOf) {
    JsonNode root;
    try {
      root = objectMapper.readTree(rawJson);
    } catch (RuntimeException ex) {
      throw schema("MODEL_JSON_INVALID");
    }
    if (root == null || !root.isObject()) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.ROOT_TYPE);
    }
    if (ModelOutputGuard.containsScoreProperty(root)) {
      throw schema("MODEL_SCORE_FIELD_FORBIDDEN");
    }
    Set<String> names = new HashSet<>(root.propertyNames());
    if (!names.equals(TOP_LEVEL) && !names.equals(LEGACY_TOP_LEVEL)) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.TOP_LEVEL_KEYS);
    }
    // 历史模型字段不参与招聘事实；忽略其内容，避免非关键待核实文本使整份简历重跑。
    ((ObjectNode) root).putArray("unknownItems");
    if (modelOutput) normalizeModelContacts((ObjectNode) root);
    normalizePairedYearRanges((ObjectNode) root);
    if (modelOutput) normalizeImpossibleModelDateRanges((ObjectNode) root);
    if (modelOutput) recoverExplicitHeaderAge((ObjectNode) root, pages);
    ResumeFacts facts;
    try {
      facts = objectMapper.treeToValue(root, ResumeFacts.class);
    } catch (RuntimeException ex) {
      String path = deserializationPath(ex, root);
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.DESERIALIZATION,
          "exceptionClass=" + ex.getClass().getSimpleName()
              + ",path=" + path + ",nodeType=" + diagnosticNodeType(root, path));
    }
    validateFacts(facts, asOf);
    if (modelOutput && facts.candidate().birthDate() != null) {
      return deriveAge((ObjectNode) root, facts, asOf);
    }
    return facts;
  }

  /** 模型可能为可选联系方式编造占位文本；按提示词将无效文本视作未知，不丢弃整份事实。 */
  private static void normalizeModelContacts(ObjectNode root) {
    if (!(root.path("candidate") instanceof ObjectNode candidate)) return;
    normalizeOptionalContact(candidate, "email", TalentConstants.CANDIDATE_EMAIL_MAX_LENGTH, EMAIL);
    normalizeOptionalContact(candidate, "phone", TalentConstants.CANDIDATE_PHONE_MAX_LENGTH, PHONE);
  }

  /** 仅在模型遗漏年龄时，从首页个人信息区的唯一明确数字标签补齐，不从岗位条件或他人信息推断。 */
  private static void recoverExplicitHeaderAge(ObjectNode root, List<PageText> pages) {
    if (!(root.path("candidate") instanceof ObjectNode candidate)
        || present(candidate.get("age")) || present(candidate.get("birthDate"))
        || pages == null) return;
    String firstPage = pages.stream().filter(page -> page.pageNo() == 1)
        .map(PageText::text).filter(text -> text != null && !text.isBlank())
        .findFirst().orElse(null);
    if (firstPage == null) return;
    String header = firstPage.substring(0, Math.min(AGE_HEADER_LIMIT, firstPage.length()));
    var matches = EXPLICIT_HEADER_AGE.matcher(header);
    Integer age = null;
    while (matches.find()) {
      if (age != null) return;
      String before = header.substring(0, matches.start());
      if (AGE_REQUIREMENT_HEADINGS.stream().anyMatch(before::contains)) return;
      if (OTHER_PERSON_LABELS.stream().anyMatch(before::contains)) return;
      if (matches.start() >= 40
          && PERSONAL_HEADER_LABELS.stream().noneMatch(before::contains)) return;
      int parsed = Integer.parseInt(matches.group(1));
      if (parsed < TalentConstants.MIN_RESUME_AGE || parsed > TalentConstants.MAX_RESUME_AGE)
        return;
      age = parsed;
    }
    if (age != null) candidate.put("age", age);
  }

  private static boolean present(JsonNode node) {
    return node != null && !node.isNull();
  }

  private static void normalizeOptionalContact(
      ObjectNode candidate, String field, int maxLength, Pattern pattern) {
    JsonNode node = candidate.get(field);
    if (node == null || node.isNull()) return;
    boolean numericPhone = "phone".equals(field) && node.isIntegralNumber();
    if (!node.isString() && !numericPhone) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.CONTACT_TYPE);
    }
    // 兼容旧版 Jackson 将纯数字电话转为字符串后仍可通过原有电话校验的行为。
    String value = numericPhone ? node.asString() : node.stringValue();
    if (value.isBlank() || value.length() > maxLength || !pattern.matcher(value).matches()) {
      candidate.putNull(field);
    } else if (numericPhone) {
      candidate.put(field, value);
    }
  }

  /** 模型偶尔把明确的 YYYY-YYYY 范围放进单个日期字段；无损拆回已有配对字段。 */
  private static void normalizePairedYearRanges(ObjectNode root) {
    normalizePairedYearRanges(root.path("workExperiences"), "startDate", "endDate");
    normalizePairedYearRanges(root.path("projectExperiences"), "startDate", "endDate");
    normalizePairedYearRanges(root.path("educations"), "startDate", "endDate");
    normalizePairedYearRanges(root.path("credentials"), "issuedDate", "expiryDate");
  }

  private static void normalizePairedYearRanges(
      JsonNode values, String startField, String endField) {
    if (!values.isArray()) return;
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode item)) continue;
      JsonNode start = item.path(startField);
      JsonNode end = item.path(endField);
      if (!start.isString()
          || !(end.isMissingNode() || end.isNull() || (end.isString() && end.stringValue().isBlank()))) {
        continue;
      }
      var range = YEAR_RANGE.matcher(start.stringValue());
      if (!range.matches()) continue;
      item.put(startField, range.group(1));
      item.put(endField, range.group(2));
    }
  }

  /** 模型给出不可能的起止顺序时，两端均视为未知，避免把猜测日期写成事实。 */
  private static void normalizeImpossibleModelDateRanges(ObjectNode root) {
    normalizeImpossibleModelDateRanges(root.path("workExperiences"), "startDate", "endDate");
    normalizeImpossibleModelDateRanges(root.path("projectExperiences"), "startDate", "endDate");
    normalizeImpossibleModelDateRanges(root.path("educations"), "startDate", "endDate");
    normalizeImpossibleModelDateRanges(root.path("credentials"), "issuedDate", "expiryDate");
  }

  private static void normalizeImpossibleModelDateRanges(
      JsonNode values, String startField, String endField) {
    if (!values.isArray()) return;
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode item)) continue;
      JsonNode start = item.get(startField);
      JsonNode end = item.get(endField);
      if (start == null || !start.isString()
          || (end != null && !end.isNull() && !end.isString())) continue;
      try {
        var a = ResumeDateValue.parse(start.stringValue());
        var b = ResumeDateValue.parse(end == null || end.isNull() ? null : end.stringValue());
        if (impossibleDateRange(a, b)) {
          item.putNull(startField);
          item.putNull(endField);
        }
      } catch (IllegalArgumentException ignored) {
        // 非法日期仍由事实校验拒绝，不能混同于顺序错误。
      }
    }
  }

  private static String deserializationPath(RuntimeException failure, JsonNode root) {
    if (failure instanceof JacksonException jackson) {
      StringBuilder path = new StringBuilder();
      for (var reference : jackson.getPath()) {
        if (path.length() >= 120) break;
        String field = reference.getPropertyName();
        if (field != null) path.append('/').append(
            DIAGNOSTIC_FIELDS.contains(field) ? field : "other");
        else if (reference.getIndex() >= 0) path.append('/').append(reference.getIndex());
      }
      if (!path.isEmpty()) return path.toString();
    }
    String nullItem = firstNullArrayItem(root, "", 0);
    return nullItem == null ? "none" : nullItem;
  }

  private static String firstNullArrayItem(JsonNode node, String path, int depth) {
    if (depth > 6) return null;
    if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        JsonNode item = node.get(i);
        String itemPath = path + "/" + i;
        if (item.isNull()) return itemPath;
        String nested = firstNullArrayItem(item, itemPath, depth + 1);
        if (nested != null) return nested;
      }
    } else if (node.isObject()) {
      for (String field : node.propertyNames()) {
        if (!DIAGNOSTIC_FIELDS.contains(field)) continue;
        String nested = firstNullArrayItem(node.get(field), path + "/" + field, depth + 1);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private static String diagnosticNodeType(JsonNode root, String path) {
    return path.startsWith("/") ? root.at(path).getNodeType().name() : "UNKNOWN";
  }

  /** 按日期区间计算；仅所有可能生日得到同一周岁时填入年龄。 */
  private ResumeFacts deriveAge(ObjectNode root, ResumeFacts facts, LocalDate today) {
    if (facts.candidate().age() != null) return facts;
    var birth = ResumeDateValue.parse(facts.candidate().birthDate());
    long youngest = ChronoUnit.YEARS.between(birth.latest(today), today);
    long oldest = ChronoUnit.YEARS.between(birth.earliest(today), today);
    if (youngest != oldest || youngest < TalentConstants.MIN_RESUME_AGE
        || oldest > TalentConstants.MAX_RESUME_AGE) return facts;
    ((ObjectNode) root.path("candidate")).put("age", (int) youngest);
    return objectMapper.treeToValue(root, ResumeFacts.class);
  }

  public ResumeFacts validateHumanFacts(ResumeFacts facts) {
    validateFacts(facts, LocalDate.now(clock));
    return facts;
  }

  /** 校验事实列表。 */
  private static void validateFacts(ResumeFacts f, LocalDate asOf) {
    if (f == null || f.candidate() == null) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.CANDIDATE_MISSING);
    }
    text(f.candidate().fullName(), TalentConstants.MAX_RESUME_FULL_NAME_LENGTH, true, "CANDIDATE_NAME");
    formattedText(
        f.candidate().email(), TalentConstants.CANDIDATE_EMAIL_MAX_LENGTH, EMAIL, true, "CANDIDATE_EMAIL");
    formattedText(
        f.candidate().phone(), TalentConstants.CANDIDATE_PHONE_MAX_LENGTH, PHONE, true, "CANDIDATE_PHONE");
    text(f.candidate().currentRole(), TalentConstants.MAX_RESUME_ROLE_LENGTH, true, "CANDIDATE_ROLE");
    text(f.candidate().city(), TalentConstants.MAX_RESUME_CITY_LENGTH, true, "CANDIDATE_CITY");
    if (f.candidate().birthDate() != null) {
      try {
        var birth = ResumeDateValue.parse(f.candidate().birthDate());
        if (birth == null || birth.isPresent()
            || birth.earliest(LocalDate.MIN).isAfter(asOf))
          throw schema("MODEL_BIRTH_DATE_INVALID");
      } catch (IllegalArgumentException error) {
        throw schema("MODEL_BIRTH_DATE_INVALID");
      }
    }
    if (f.candidate().age() != null
        && (f.candidate().age() < TalentConstants.MIN_RESUME_AGE
            || f.candidate().age() > TalentConstants.MAX_RESUME_AGE))
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.CANDIDATE_AGE);
    if (f.candidate().gender() != null && !CandidateGender.supports(f.candidate().gender()))
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.CANDIDATE_GENDER);
    text(
        f.candidate().jobIntention(), TalentConstants.MAX_RESUME_JOB_INTENTION_LENGTH, true,
        "CANDIDATE_INTENTION");
    sized(f.workExperiences(), TalentConstants.MAX_RESUME_WORK_EXPERIENCES);
    unique(f.workExperiences(), WorkExperience::id);
    for (WorkExperience v : f.workExperiences()) {
      item(v.id());
      text(v.employer(), TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH, true, "WORK_EMPLOYER");
      text(v.role(), TalentConstants.MAX_RESUME_ROLE_LENGTH, true, "WORK_ROLE");
      dateRange(v.startDate(), v.endDate());
      text(v.description(), TalentConstants.MAX_RESUME_DESCRIPTION_LENGTH, true, "WORK_DESCRIPTION");
      strings(
          v.responsibilities(),
          TalentConstants.MAX_RESUME_ITEM_TEXTS,
          TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH, "WORK_RESPONSIBILITY");
      strings(
          v.achievements(),
          TalentConstants.MAX_RESUME_ITEM_TEXTS,
          TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH, "WORK_ACHIEVEMENT");
    }
    sized(f.projectExperiences(), TalentConstants.MAX_RESUME_PROJECT_EXPERIENCES);
    unique(f.projectExperiences(), ProjectExperience::id);
    for (ProjectExperience v : f.projectExperiences()) {
      item(v.id());
      text(v.projectName(), TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH, true, "PROJECT_NAME");
      text(v.role(), TalentConstants.MAX_RESUME_ROLE_LENGTH, true, "PROJECT_ROLE");
      dateRange(v.startDate(), v.endDate());
      text(v.description(), TalentConstants.MAX_RESUME_DESCRIPTION_LENGTH, true, "PROJECT_DESCRIPTION");
      strings(
          v.technologies(),
          TalentConstants.MAX_RESUME_ITEM_TEXTS,
          TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH, "PROJECT_TECHNOLOGY");
      strings(
          v.achievements(),
          TalentConstants.MAX_RESUME_ITEM_TEXTS,
          TalentConstants.MAX_RESUME_LIST_TEXT_LENGTH, "PROJECT_ACHIEVEMENT");
    }
    sized(f.educations(), TalentConstants.MAX_RESUME_EDUCATIONS);
    unique(f.educations(), Education::id);
    for (Education v : f.educations()) {
      item(v.id());
      text(v.school(), TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH, true, "EDUCATION_SCHOOL");
      text(v.degree(), TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH, true, "EDUCATION_DEGREE");
      text(v.major(), TalentConstants.MAX_RESUME_ROLE_LENGTH, true, "EDUCATION_MAJOR");
      dateRange(v.startDate(), v.endDate());
      text(v.learningMode(), TalentConstants.MAX_RESUME_CODE_LENGTH, true, "EDUCATION_MODE");
    }
    sized(f.skills(), TalentConstants.MAX_RESUME_SKILLS);
    unique(f.skills(), Skill::id);
    for (Skill v : f.skills()) {
      item(v.id());
      text(v.name(), TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH, false, "SKILL_NAME");
      text(v.level(), TalentConstants.MAX_RESUME_CODE_LENGTH, true, "SKILL_LEVEL");
      if (v.years() != null
          && (v.years() < TalentConstants.MIN_RESUME_YEARS_VALUE
              || v.years() > TalentConstants.MAX_RESUME_YEARS_VALUE
              || !Double.isFinite(v.years())))
        throw schema("MODEL_SCHEMA_INVALID", RuleCode.SKILL_YEARS);
    }
    sized(f.credentials(), TalentConstants.MAX_RESUME_CREDENTIALS);
    unique(f.credentials(), Credential::id);
    for (Credential v : f.credentials()) {
      item(v.id());
      text(v.name(), TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH, false, "CREDENTIAL_NAME");
      text(v.issuer(), TalentConstants.MAX_RESUME_ORGANIZATION_NAME_LENGTH, true, "CREDENTIAL_ISSUER");
      dateRange(v.issuedDate(), v.expiryDate());
    }
    sized(f.languages(), TalentConstants.MAX_RESUME_LANGUAGES);
    unique(f.languages(), Language::id);
    for (Language v : f.languages()) {
      item(v.id());
      text(v.name(), TalentConstants.MAX_RESUME_LEVEL_LENGTH, false, "LANGUAGE_NAME");
      text(v.level(), TalentConstants.MAX_RESUME_LEVEL_LENGTH, true, "LANGUAGE_LEVEL");
    }
    strings(
        f.industryTags(),
        TalentConstants.MAX_RESUME_INDUSTRY_TAGS,
        TalentConstants.MAX_RESUME_SHORT_LABEL_LENGTH, "INDUSTRY_TAG");
    strings(
        f.responsibilityTags(),
        TalentConstants.MAX_RESUME_RESPONSIBILITY_TAGS,
        TalentConstants.MAX_RESUME_RESPONSIBILITY_TAG_LENGTH, "RESPONSIBILITY_TAG");
  }

  private static <T> void unique(List<T> values, Function<T, String> id) {
    Set<String> seen = new HashSet<>();
    for (T value : values)
      if (value == null || !seen.add(id.apply(value))) throw schema("MODEL_ITEM_ID_DUPLICATE");
  }

  private static void item(String value) {
    if (value == null || !ITEM_ID.matcher(value).matches()) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.ITEM_ID);
    }
  }

  private static void strings(List<String> values, int maxItems, int maxLength, String fieldCode) {
    sized(values, maxItems);
    for (String value : values) text(value, maxLength, false, fieldCode);
  }

  private static void text(String value, int max, boolean nullable, String fieldCode) {
    if (value == null) {
      if (!nullable) throw schema("MODEL_SCHEMA_INVALID", RuleCode.TEXT_BOUNDARY,
          "field=" + fieldCode + ",length=-1,max=" + max);
      return;
    }
    if ((!nullable && value.isBlank()) || value.length() > max)
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.TEXT_BOUNDARY,
          "field=" + fieldCode + ",length=" + value.length() + ",max=" + max);
  }

  private static void formattedText(
      String value, int max, Pattern pattern, boolean nullable, String fieldCode) {
    text(value, max, nullable, fieldCode);
    if (value != null && !value.isBlank() && !pattern.matcher(value).matches()) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.CONTACT_FORMAT);
    }
  }

  private static void dateRange(String start, String end) {
    try {
      var a = ResumeDateValue.parse(start);
      var b = ResumeDateValue.parse(end);
      if (impossibleDateRange(a, b)) throw schema("MODEL_DATE_RANGE_INVALID");
    } catch (IllegalArgumentException ex) {
      throw schema("MODEL_DATE_INVALID");
    }
  }

  private static boolean impossibleDateRange(ResumeDateValue start, ResumeDateValue end) {
    return start != null && (start.isPresent()
        || end != null && !end.isPresent()
            && end.latest(LocalDate.MAX).isBefore(start.earliest(LocalDate.MIN)));
  }

  private static void sized(List<?> value, int max) {
    if (value == null || value.size() > max) {
      throw schema("MODEL_SCHEMA_INVALID", RuleCode.LIST_BOUNDARY);
    }
  }

  private static ContractViolation schema(String code) {
    return schema(code, RuleCode.GENERAL);
  }

  private static ContractViolation schema(String code, String ruleCode) {
    return new ContractViolation(
        AiOutputValidationStatus.SCHEMA_INVALID, code, "模型输出不符合简历事实契约", ruleCode);
  }

  private static ContractViolation schema(String code, String ruleCode, String diagnostic) {
    return new ContractViolation(
        AiOutputValidationStatus.SCHEMA_INVALID, code, "模型输出不符合简历事实契约",
        ruleCode, diagnostic);
  }

  private static final class RuleCode {
    private static final String GENERAL = "GENERAL";
    private static final String ROOT_TYPE = "ROOT_TYPE";
    private static final String TOP_LEVEL_KEYS = "TOP_LEVEL_KEYS";
    private static final String DESERIALIZATION = "DESERIALIZATION";
    private static final String CANDIDATE_MISSING = "CANDIDATE_MISSING";
    private static final String CANDIDATE_AGE = "CANDIDATE_AGE";
    private static final String CANDIDATE_GENDER = "CANDIDATE_GENDER";
    private static final String SKILL_YEARS = "SKILL_YEARS";
    private static final String ITEM_ID = "ITEM_ID";
    private static final String TEXT_BOUNDARY = "TEXT_BOUNDARY";
    private static final String CONTACT_TYPE = "CONTACT_TYPE";
    private static final String CONTACT_FORMAT = "CONTACT_FORMAT";
    private static final String LIST_BOUNDARY = "LIST_BOUNDARY";

    private RuleCode() {}
  }

  @Getter
  @Accessors(fluent = true)
  public static final class ContractViolation extends RuntimeException {
    private final AiOutputValidationStatus validationStatus;
    private final String errorCode;
    private final String safeDetail;
    private final String ruleCode;
    private final String diagnostic;

    ContractViolation(
        AiOutputValidationStatus validationStatus, String errorCode, String safeDetail) {
      this(validationStatus, errorCode, safeDetail, RuleCode.GENERAL);
    }

    private ContractViolation(
        AiOutputValidationStatus validationStatus, String errorCode, String safeDetail,
        String ruleCode) {
      this(validationStatus, errorCode, safeDetail, ruleCode, "none");
    }

    private ContractViolation(
        AiOutputValidationStatus validationStatus, String errorCode, String safeDetail,
        String ruleCode, String diagnostic) {
      super(safeDetail);
      this.validationStatus = validationStatus;
      this.errorCode = errorCode;
      this.safeDetail = safeDetail;
      this.ruleCode = ruleCode;
      this.diagnostic = diagnostic;
    }
  }
}

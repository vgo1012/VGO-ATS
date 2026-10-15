package com.ezaigc.hr.talent.application.manager;

import static com.ezaigc.hr.talent.constant.TalentConstants.MAX_RESUME_EXTRACTION_PAGES;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_FACT_SCHEMA_VERSION;
import static com.ezaigc.hr.talent.constant.TalentConstants.RESUME_PARSE_PROMPT_VERSION;

import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort;
import com.ezaigc.hr.common.infrastructure.ai.RawAiArchivePort.ArchiveResult;
import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.talent.api.ObjectStoragePort;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.PrecomputedAssessment;
import com.ezaigc.hr.talent.api.ResumeIntakeScoringService.ScoringPreparation;
import com.ezaigc.hr.talent.application.provider.CombinedIntakeModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedDocument;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.CandidateIdentity;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ParseRequest;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ResumeFacts;
import com.ezaigc.hr.talent.domain.dto.IntakeParsedSourceDTO;
import com.ezaigc.hr.talent.domain.dto.IntakeScoringPreparationDTO;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import com.ezaigc.hr.talent.service.validator.ResumeParseContractValidator;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 在候选人建立前执行可共享的文档抽取、模型解析、归档与事实校验。 */
@Service
public class ResumeIntakeParsingManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ResumeIntakeParsingManager.class);
  private static final Pattern SOURCE_PHONE = Pattern.compile(
      "(?<![0-9])(?:\\+?86[\\s-]?)?1[3-9](?:[\\s-]?[0-9]){9}(?![0-9])");
  private static final Pattern SOURCE_EMAIL = Pattern.compile(
      "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern THIRD_PARTY_CONTACT = Pattern.compile(
      "招聘方|招聘专员|推荐人|推荐者|人事|猎头|联系人|\\bHR\\b", Pattern.CASE_INSENSITIVE);
  @Resource private Clock clock;

  @Resource private ObjectStoragePort storage;
  @Resource private ResumeDocumentExtractionPort extraction;
  @Resource private ResumeParseModelPort model;
  @Resource private ObjectProvider<CombinedIntakeModelPort> combinedModelProvider;
  @Resource private ResumeIntakeScoringService scoring;
  @Resource private TalentIntakePersistenceService intakes;
  @Resource private RawAiArchivePort archive;
  @Resource private ResumeParseContractValidator validator;
  @Resource private ObjectMapper json;

  public ParsedIntake parse(ResumeIntakeProcessing processing) throws IOException {
    ExtractedDocument document;
    try (InputStream content = storage.open(processing.getObjectKey())) {
      document = extraction.extract(processing.getOriginalFileName(), content);
    }
    requireCompleteDocument(document);
    List<PageText> pages = document.pages().stream()
        .map(page -> new PageText(page.pageNo(), page.text())).toList();
    ModelCall call = callModel(processing, pages);
    ArchiveResult archived = archive.archive(
        "ai/raw/resume-intake/" + processing.getId() + "/" + processing.getLeaseOwner() + ".json",
        call.rawJson());
    LocalDate ageAsOf = LocalDate.now(clock);
    ResumeFacts facts = validator.validateModel(call.resumeFactsJson(), pages, ageAsOf);
    String contactRecheckArchiveKey = null;
    if (call.combined() && facts.candidate().phone() == null && facts.candidate().email() == null
        && pages.stream().anyMatch(page -> SOURCE_PHONE.matcher(page.text()).find()
            || SOURCE_EMAIL.matcher(page.text()).find())) {
      try {
        ModelResult rechecked = model.parse(new ParseRequest(
            RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION,
            processing.getOriginalFileName(), pages));
        if (rechecked == null || rechecked.rawJson() == null) {
          throw new IllegalStateException("resume contact recheck returned no result");
        }
        ArchiveResult recheckedArchive = archive.archive(
            "ai/raw/resume-intake/" + processing.getId() + "/" + processing.getLeaseOwner()
                + "-contact-recheck.json", rechecked.rawJson());
        ResumeFacts recheckedFacts = validator.validateModel(rechecked.rawJson(), pages, ageAsOf);
        if (sameCandidate(facts, recheckedFacts)) {
          String phone = supportedContact(pages, facts.candidate().fullName(),
              recheckedFacts.candidate().phone(), SOURCE_PHONE, true);
          String email = supportedContact(pages, facts.candidate().fullName(),
              recheckedFacts.candidate().email(), SOURCE_EMAIL, false);
          if (phone != null || email != null) {
            facts = withRecheckedContacts(facts, phone, email);
            contactRecheckArchiveKey = recheckedArchive.objectKey();
            LOGGER.info("resume-intake-contact-recheck-recovered processingId={}", processing.getId());
          }
        }
      } catch (IOException | RuntimeException failure) {
        LOGGER.warn("resume-intake-contact-recheck-skipped processingId={}", processing.getId(),
            SafeLogException.from(failure));
      }
    }
    var source = new IntakeParsedSourceDTO(document.extractorVersion(), document.route(),
        document.pageCount(), document.successfulPages(), document.fallbackPages(),
        document.printableRatio(), document.coverageRatio(), document.pages().stream()
            .map(page -> new IntakeParsedSourceDTO.Page(page.pageNo(), page.route(), page.text(),
                page.characterCount(), page.printableRatio(), page.qualityScore(), page.contentHash())).toList(),
        RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION,
        call.model().inputTokens(), call.model().outputTokens(), call.model().durationMillis(), ageAsOf,
        call.precomputedAssessment());
    source.setContactRecheckArchiveKey(contactRecheckArchiveKey);
    return new ParsedIntake(json.writeValueAsString(source), json.writeValueAsString(facts),
        facts, call.model(), archived);
  }

  private ModelCall callModel(ResumeIntakeProcessing processing, List<PageText> pages) {
    ScoringPreparation preparation = prepareScoring(processing);
    CombinedIntakeModelPort combined = combinedModelProvider == null
        ? null : combinedModelProvider.getIfAvailable();
    if (preparation != null && combined != null) {
      var result = combined.evaluate(new CombinedIntakeModelPort.Request(
          RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION,
          preparation.schemaVersion(), preparation.promptVersion(), preparation.evaluatedOn(),
          processing.getOriginalFileName(), pages, preparation.jdFactsJson()));
      if (result == null || result.rawJson() == null) {
        throw new IllegalStateException("combined intake model returned no result");
      }
      JsonNode root = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .readTree(result.rawJson());
      JsonNode resumeFacts = root == null ? null : root.get("resumeFacts");
      if (resumeFacts == null || !resumeFacts.isObject()) {
        throw new IllegalStateException("combined intake model returned no resume facts");
      }
      JsonNode assessment = root.get("assessment");
      PrecomputedAssessment precomputed = assessment == null || !assessment.isObject()
          ? null
          : new PrecomputedAssessment(preparation.jdFactSnapshotId(), preparation.promptVersion(),
              preparation.schemaVersion(), preparation.evaluatedOn(), result.provider(),
              result.modelVersion(), assessment.toString(), result.inputTokens(),
              result.outputTokens(), result.durationMillis());
      return new ModelCall(resumeFacts.toString(), result.rawJson(),
          new ModelResult(result.provider(), result.modelVersion(), resumeFacts.toString(),
              result.inputTokens(), result.outputTokens(), result.durationMillis()), precomputed, true);
    }
    ModelResult result = model.parse(new ParseRequest(
        RESUME_FACT_SCHEMA_VERSION, RESUME_PARSE_PROMPT_VERSION,
        processing.getOriginalFileName(), pages));
    if (result == null || result.rawJson() == null) {
      throw new IllegalStateException("resume parse model returned no result");
    }
    return new ModelCall(result.rawJson(), result.rawJson(), result, null, false);
  }

  private static boolean sameCandidate(ResumeFacts original, ResumeFacts rechecked) {
    String name = original.candidate().fullName();
    return name != null && !name.isBlank() && name.equals(rechecked.candidate().fullName());
  }

  private static String supportedContact(
      List<PageText> pages, String name, String proposed, Pattern pattern, boolean phone) {
    if (proposed == null || proposed.isBlank()) return null;
    String expected = phone ? phoneDigits(proposed) : proposed.toLowerCase(Locale.ROOT);
    var distinct = new HashSet<String>();
    boolean nearCandidate = false;
    for (PageText page : pages) {
      var matcher = pattern.matcher(page.text());
      while (matcher.find()) {
        String found = phone ? phoneDigits(matcher.group())
            : matcher.group().toLowerCase(Locale.ROOT);
        distinct.add(found);
        if (page.pageNo() == 1 && found.equals(expected)
            && nearCandidateName(page.text(), name, matcher.start())
            && !thirdPartyLabelNearby(page.text(), matcher.start(), matcher.end())) {
          nearCandidate = true;
        }
      }
    }
    return nearCandidate && distinct.size() == 1 ? proposed : null;
  }

  private static String phoneDigits(String value) {
    String digits = value.replaceAll("[^0-9]", "");
    return digits.length() == 13 && digits.startsWith("86") ? digits.substring(2) : digits;
  }

  private static boolean nearCandidateName(String text, String name, int position) {
    for (int index = text.indexOf(name); index >= 0; index = text.indexOf(name, index + 1)) {
      if (index <= position && position - index <= 100
          && !THIRD_PARTY_CONTACT.matcher(text.substring(index, position)).find()) return true;
    }
    return false;
  }

  private static boolean thirdPartyLabelNearby(String text, int start, int end) {
    return THIRD_PARTY_CONTACT.matcher(text.substring(Math.max(0, start - 30),
        Math.min(text.length(), end + 30))).find();
  }

  private static ResumeFacts withRecheckedContacts(ResumeFacts original, String phone, String email) {
    var before = original.candidate();
    var candidate = new CandidateIdentity(before.fullName(), email,
        phone, before.currentRole(), before.city(), before.age(), before.gender(),
        before.jobIntention(), before.birthDate());
    return new ResumeFacts(candidate, original.workExperiences(), original.projectExperiences(),
        original.educations(), original.skills(), original.credentials(), original.languages(),
        original.industryTags(), original.responsibilityTags(), original.unknownItems());
  }

  private ScoringPreparation prepareScoring(ResumeIntakeProcessing processing) {
    if (intakes == null || scoring == null || combinedModelProvider == null) return null;
    IntakeScoringPreparationDTO intake = intakes.findScoringPreparationIntake(processing.getId());
    if (intake == null || intake.getJdVersionId() == null) return null;
    String actorId = firstNonBlank(intake.getHandledByUserId(), intake.getSourceRecruiterUserId(),
        intake.getCreateId());
    if (actorId == null) return null;
    try {
      return scoring.prepare(intake.getJdVersionId(), actorId);
    } catch (RuntimeException failure) {
      LOGGER.warn("resume-intake-combined-scoring-preparation-skipped processingId={} intakeId={}",
          processing.getId(), intake.getId(), SafeLogException.from(failure));
      return null;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) if (value != null && !value.isBlank()) return value;
    return null;
  }

  @Value
  @Accessors(fluent = true)
  private static class ModelCall {
    String resumeFactsJson;
    String rawJson;
    ModelResult model;
    PrecomputedAssessment precomputedAssessment;
    boolean combined;
  }

  @Value
  @Accessors(fluent = true)
  public static class ParsedIntake {
    String pagesJson;
    String factsJson;
    ResumeFacts facts;
    ModelResult model;
    ArchiveResult archive;
  }

  private static void requireCompleteDocument(ExtractedDocument document) {
    if (document == null || document.pages() == null || document.pageCount() < 1
        || document.pageCount() > MAX_RESUME_EXTRACTION_PAGES
        || document.pages().size() != document.pageCount()
        || document.successfulPages() != document.pageCount()
        || !Double.isFinite(document.coverageRatio()) || document.coverageRatio() != 1.0) {
      throw new IllegalArgumentException("document extraction coverage is incomplete");
    }
    var numbers = new HashSet<Integer>();
    for (var page : document.pages()) {
      if (page.pageNo() < 1 || page.pageNo() > document.pageCount()
          || !numbers.add(page.pageNo()) || page.text() == null) {
        throw new IllegalArgumentException("document extraction page is invalid");
      }
    }
  }
}

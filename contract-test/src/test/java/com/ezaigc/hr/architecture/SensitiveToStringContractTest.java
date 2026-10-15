package com.ezaigc.hr.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ezaigc.hr.identity.domain.dto.IdentityPrincipal;
import com.ezaigc.hr.identity.domain.entity.UserAccountRecord;
import com.ezaigc.hr.identity.domain.request.AuthChangePasswordRequest;
import com.ezaigc.hr.identity.domain.request.AuthLoginRequest;
import com.ezaigc.hr.identity.domain.response.AuthCsrfResponse;
import com.ezaigc.hr.identity.domain.response.CreatedUserResponse;
import com.ezaigc.hr.identity.domain.response.ResetCredentialResponse;
import com.ezaigc.hr.intelligence.search.domain.response.TalentSearchResultResponse;
import com.ezaigc.hr.integration.config.IntegrationProperties;
import com.ezaigc.hr.talent.api.ResumeFactQueryService.CurrentFactContact;
import com.ezaigc.hr.talent.api.ResumeFactSnapshotData.CandidateFacts;
import com.ezaigc.hr.talent.application.provider.ResumeDocumentExtractionPort.ExtractedPage;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.ModelResult;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort.PageText;
import com.ezaigc.hr.talent.domain.dto.ConfirmIntakeDTO;
import com.ezaigc.hr.talent.domain.dto.CorrectionMutationDTO;
import com.ezaigc.hr.talent.domain.dto.UpdateCandidateDTO;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.DownloadAuthorization;
import com.ezaigc.hr.talent.domain.entity.FactSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPage;
import com.ezaigc.hr.talent.domain.entity.ResumeExtractionPageEntity;
import com.ezaigc.hr.talent.domain.request.TalentIntakeCandidateRequest;
import com.ezaigc.hr.talent.domain.request.TalentIntakeConfirmRequest;
import com.ezaigc.hr.talent.domain.response.CandidateResponse;
import com.ezaigc.hr.talent.domain.response.DownloadGrantResponse;
import com.ezaigc.hr.talent.domain.response.FactSnapshotResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.ezaigc.hr.talent.application.provider.ResumeParseModelPort;
import java.util.List;

class SensitiveToStringContractTest {

  private static final String SECRET = "do-not-log-this-secret";

  @Test
  void sensitiveCarriersDoNotExposeValuesInToString() {
    Map<String, Object> carriers = new LinkedHashMap<>();
    ObjectMapper json = new ObjectMapper();
    carriers.put("login request", new AuthLoginRequest("user@example.com", SECRET));
    carriers.put("password change", new AuthChangePasswordRequest(SECRET, SECRET));
    carriers.put("created user credential", new CreatedUserResponse("user", SECRET, true, "0"));
    carriers.put("reset credential", new ResetCredentialResponse("user", SECRET, true, "1"));
    carriers.put(
        "security principal",
        new IdentityPrincipal(
            "01JUSER0000000000000000000",
            "User",
            "user@example.com",
            SECRET,
            "01JDEPT0000000000000000000",
            Set.of("USER"),
            Set.of("talent:read"),
            false,
            0,
            true));
    carriers.put(
        "user account",
        new UserAccountRecord(
            "01JUSER0000000000000000000",
            "User",
            "user@example.com",
            "user@example.com",
            SECRET,
            "01JDEPT0000000000000000000",
            "ACTIVE",
            false,
            0));
    var objectStorage = new IntegrationProperties.ObjectStorage();
    objectStorage.setAccessKey(SECRET);
    objectStorage.setSecretKey(SECRET);
    carriers.put("object storage", objectStorage);
    carriers.put(
        "AI archive",
        new IntegrationProperties.Ai(SECRET, new IntegrationProperties.Parsing(false)));
    carriers.put(
        "Elasticsearch",
        new IntegrationProperties.Elasticsearch(
            "http://elasticsearch", "resume", SECRET, "user", SECRET, null, 1));
    carriers.put("CSRF response", new AuthCsrfResponse("X-CSRF-TOKEN", "_csrf", SECRET));
    carriers.put("download grant", new DownloadGrantResponse(SECRET, Instant.EPOCH));
    carriers.put(
        "download authorization",
        new DownloadAuthorization(
            "01JGRANT000000000000000000",
            SECRET,
            "01JRESUME00000000000000000",
            "01JUSER0000000000000000000",
            Instant.EPOCH,
            null));
    Candidate candidate = new Candidate();
    candidate.setPrimaryEmail(SECRET);
    candidate.setPrimaryPhone(SECRET);
    carriers.put("candidate", candidate);
    carriers.put(
        "candidate request",
        new TalentIntakeCandidateRequest("User", SECRET, SECRET, null, null, "0"));
    carriers.put(
        "candidate confirm request",
        new TalentIntakeConfirmRequest(null, "User", SECRET, SECRET, "0"));
    carriers.put("candidate confirm DTO", new ConfirmIntakeDTO(null, "User", SECRET, SECRET, 0));
    carriers.put(
        "candidate update DTO",
        new UpdateCandidateDTO("User", SECRET, SECRET, null, null, 0));
    carriers.put(
        "candidate response",
        new CandidateResponse(
            "candidate-id",
            "User",
            SECRET,
            SECRET,
            "owner-id",
            "department-id",
            "ACTIVE",
            "0",
            "creator-id",
            Instant.EPOCH,
            Instant.EPOCH));
    FactSnapshot factSnapshot = new FactSnapshot();
    factSnapshot.setEmail(SECRET);
    factSnapshot.setPhone(SECRET);
    carriers.put("resume fact snapshot", factSnapshot);
    carriers.put(
        "resume fact response",
        new FactSnapshotResponse(
            "snapshot-id",
            "version-id",
            "parse-id",
            null,
            1,
            "AI_PARSE",
            "Y",
            "User",
            SECRET,
            SECRET,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.EPOCH));
    carriers.put(
        "current fact contact", new CurrentFactContact("snapshot-id", SECRET, SECRET));
    carriers.put(
        "resume candidate facts",
        new CandidateFacts("User", SECRET, SECRET, null, null, null, null, null));
    carriers.put(
        "parse model candidate",
        new ResumeParseModelPort.CandidateIdentity(
            "User", SECRET, SECRET, null, null, null, null, null));
    TalentSearchResultResponse searchResult = new TalentSearchResultResponse();
    searchResult.setEmail(SECRET);
    searchResult.setPhone(SECRET);
    carriers.put("talent search result", searchResult);
    carriers.put("extracted resume entity", new ResumeExtractionPageEntity(1, SECRET));
    ResumeExtractionPage persistedPage = new ResumeExtractionPage();
    persistedPage.setExtractedText(SECRET);
    carriers.put("persisted resume page", persistedPage);
    carriers.put(
        "extraction provider page", new ExtractedPage(1, "NATIVE_TEXT", SECRET, 1, 1, 1, "hash"));
    carriers.put("model input page", new PageText(1, SECRET));
    carriers.put("raw model result", new ModelResult("provider", "model", SECRET, 1, 1, 1));
    carriers.put(
        "correction mutation",
        new CorrectionMutationDTO(
            "/candidate/email",
            json.valueToTree(SECRET),
            json.valueToTree(SECRET)));

    carriers.forEach(
        (description, value) ->
            assertFalse(
                value.toString().contains(SECRET),
                () -> description + " exposes sensitive data through toString"));
  }
}

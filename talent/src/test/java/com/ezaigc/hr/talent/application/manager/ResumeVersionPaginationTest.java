package com.ezaigc.hr.talent.application.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ezaigc.hr.common.web.error.PlatformException;
import com.ezaigc.hr.common.web.error.PlatformExceptions;
import com.ezaigc.hr.common.web.pagination.CursorScopes;
import com.ezaigc.hr.common.web.pagination.SignedCursorCodec;
import com.ezaigc.hr.talent.domain.entity.Candidate;
import com.ezaigc.hr.talent.domain.entity.ResumeVersion;
import com.ezaigc.hr.talent.service.CandidatePersistenceService;
import com.ezaigc.hr.talent.service.TalentIntakePersistenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class ResumeVersionPaginationTest {
  private static final String CANDIDATE = "01JCANDIDATE00000000000000";
  private static final String VERSION = "01JVERSION0000000000000000";
  private final SignedCursorCodec codec = new SignedCursorCodec("resume-version-disposable-test-key");
  private final TalentIntakePersistenceService persistence = mock(TalentIntakePersistenceService.class);
  private final CandidateReadAccess access = mock(CandidateReadAccess.class);
  private final TalentIntakeApplicationManager manager = new TalentIntakeApplicationManager();
  private final Candidate candidate = new Candidate();

  ResumeVersionPaginationTest() {
    candidate.setId(CANDIDATE);
    candidate.setStatus("ACTIVE");
    var candidates = mock(CandidatePersistenceService.class);
    when(candidates.findCandidate(CANDIDATE)).thenReturn(candidate);
    ReflectionTestUtils.setField(manager, "candidatePersistenceService", candidates);
    ReflectionTestUtils.setField(manager, "candidateReadAccess", access);
    ReflectionTestUtils.setField(manager, "persistence", persistence);
    ReflectionTestUtils.setField(manager, "cursorCodec", codec);
  }

  @Test
  void mergedOlderFileUsesItsHigherVersionNumberAsTheNextPageAnchor() {
    var merged = version(VERSION, 3, "2026-09-02T00:00:00Z");
    var retained = version("01JVERSION0000000000000001", 2, "2026-09-03T00:00:00Z");
    when(persistence.findResumeVersions(eq(CANDIDATE), any(), any(), anyInt()))
        .thenReturn(List.of(merged, retained));

    var page = manager.resumeVersions(CANDIDATE, null, 1);

    assertThat(page.getItems()).containsExactly(merged);
    assertThat(page.isHasMore()).isTrue();
    assertThatCode(() -> codec.decode(page.getNextCursor(), scope(), 2)).doesNotThrowAnyException();
    assertThat(codec.decode(page.getNextCursor(), scope(), 2)).containsExactly("3", VERSION);
    assertThatThrownBy(() -> page.getItems().add(retained)).isInstanceOf(UnsupportedOperationException.class);
    verify(access).requireAllResumes(candidate);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void walksAllMergedVersionsOnceAndRechecksAccessOnEveryPage(int limit) {
    var merged = version(VERSION, 3, "2026-09-02T00:00:00Z");
    var retained = version("01JVERSION0000000000000001", 2, "2026-09-03T00:00:00Z");
    var oldest = version("01JVERSION0000000000000002", 1, "2026-09-01T00:00:00Z");
    var all = List.of(merged, retained, oldest);
    when(persistence.findResumeVersions(CANDIDATE, null, null, limit + 1))
        .thenReturn(all.subList(0, Math.min(all.size(), limit + 1)));
    when(persistence.findResumeVersions(CANDIDATE, 3, merged.getId(), limit + 1))
        .thenReturn(List.of(retained, oldest));
    when(persistence.findResumeVersions(CANDIDATE, 2, retained.getId(), limit + 1))
        .thenReturn(List.of(oldest));
    List<Integer> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      var page = manager.resumeVersions(CANDIDATE, cursor, limit);
      seen.addAll(page.getItems().stream().map(ResumeVersion::getVersionNo).toList());
      cursor = page.getNextCursor();
      assertThat(page.isHasMore()).isEqualTo(cursor != null);
      pages++;
      assertThat(pages).isLessThanOrEqualTo(3);
    } while (cursor != null);
    assertThat(seen).containsExactly(3, 2, 1);
    verify(persistence).findResumeVersions(CANDIDATE, 2, retained.getId(), limit + 1);
    verify(access, times(pages)).requireAllResumes(candidate);
  }

  @Test
  void emptyFirstPageKeepsDefaultLimitAndHasNoCursor() {
    when(persistence.findResumeVersions(CANDIDATE, null, null, 21)).thenReturn(List.of());
    var page = manager.resumeVersions(CANDIDATE, " ", null);
    assertThat(page.getItems()).isEmpty();
    assertThat(page.isHasMore()).isFalse();
    assertThat(page.getNextCursor()).isNull();
    verify(persistence).findResumeVersions(CANDIDATE, null, null, 21);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "2147483648", "not-a-version", "2026-09-02T00:00:00Z"})
  void rejectsInvalidSignedVersionNumbersBeforeQuerying(String number) {
    assertInvalid(codec.encode(scope(), List.of(number, VERSION)));
    verifyNoInteractions(persistence);
  }

  @Test
  void rejectsMalformedAnchorIdBeforeQuerying() {
    assertInvalid(codec.encode(scope(), List.of("3", "short-id")));
    verifyNoInteractions(persistence);
  }

  @Test
  void rejectsOldTimeProtocolOtherCandidateAndWrongSignature() {
    String old = codec.encode(CursorScopes.scope("talent.resume-versions", CANDIDATE),
        List.of("2026-09-02T00:00:00Z", VERSION));
    String other = codec.encode(CursorScopes.scope("talent.resume-versions", "other", "version-desc-v2"),
        List.of("3", VERSION));
    String forged = new SignedCursorCodec("another-disposable-cursor-test-key").encode(scope(), List.of("3", VERSION));
    for (String cursor : List.of(old, other, forged)) {
      assertInvalid(cursor);
    }
    verifyNoInteractions(persistence);
  }

  @Test
  void validCursorNeverBypassesCurrentReadAuthorization() {
    doThrow(PlatformExceptions.forbidden("CANDIDATE_RESUME_ACCESS_FORBIDDEN", "无权读取"))
        .when(access).requireAllResumes(candidate);
    assertThatThrownBy(() -> manager.resumeVersions(CANDIDATE,
        codec.encode(scope(), List.of("3", VERSION)), 1))
        .isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("CANDIDATE_RESUME_ACCESS_FORBIDDEN"));
    verifyNoInteractions(persistence);
  }

  private void assertInvalid(String cursor) {
    assertThatThrownBy(() -> manager.resumeVersions(CANDIDATE, cursor, 1))
        .isInstanceOfSatisfying(PlatformException.class,
            failure -> assertThat(failure.code()).isEqualTo("CURSOR_INVALID"));
  }

  private static String scope() {
    return CursorScopes.scope("talent.resume-versions", CANDIDATE, "version-desc-v2");
  }

  private static ResumeVersion version(String id, int number, String created) {
    var version = new ResumeVersion();
    version.setId(id);
    version.setVersionNo(number);
    version.setCreateTime(Instant.parse(created));
    return version;
  }
}

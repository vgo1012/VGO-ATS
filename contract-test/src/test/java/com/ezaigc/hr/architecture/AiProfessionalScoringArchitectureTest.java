package com.ezaigc.hr.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ezaigc.hr.intelligence.matching.application.manager.AiMatchingEvaluationManager;
import com.ezaigc.hr.intelligence.matching.application.manager.MatchRunApplicationManager;
import com.ezaigc.hr.intelligence.matching.domain.service.DeterministicMatchingEngine;
import com.ezaigc.hr.intelligence.matching.mapper.MatchRunMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AiProfessionalScoringArchitectureTest {
  @Test
  void runtimeMatchingCannotFallBackToLegacyProfessionalScoring() throws Exception {
    var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.ezaigc.hr.intelligence.matching");
    noClasses().that().resideInAnyPackage("..application..", "..controller..")
        .should().dependOnClassesThat().haveSimpleName("DeterministicMatchingEngine").check(classes);
    assertFalse(DeterministicMatchingEngine.class.isAnnotationPresent(Component.class));
    assertEquals(AiMatchingEvaluationManager.class,
        MatchRunApplicationManager.class.getDeclaredField("engine").getType());
  }

  @Test
  void modelCallsAreOutsideTransactionsAndPreviewOnlyReadsSavedJudgments() {
    var evaluate = Arrays.stream(AiMatchingEvaluationManager.class.getMethods())
        .filter(method -> method.getName().equals("evaluate")).findFirst().orElseThrow();
    var preview = Arrays.stream(AiMatchingEvaluationManager.class.getMethods())
        .filter(method -> method.getName().equals("preview")).findFirst().orElseThrow();
    assertEquals(Propagation.NOT_SUPPORTED, evaluate.getAnnotation(Transactional.class).propagation());
    assertTrue(preview.getAnnotation(Transactional.class).readOnly());
    assertTrue(MatchRunMapper.BASIS.contains("r.scoring_version"));
  }
}

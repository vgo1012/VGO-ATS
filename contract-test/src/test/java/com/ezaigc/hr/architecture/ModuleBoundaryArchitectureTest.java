package com.ezaigc.hr.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import com.ezaigc.hr.common.infrastructure.concurrent.CoalescingTaskDispatcher;
import com.ezaigc.hr.common.infrastructure.persistence.SpecialSql;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import com.ezaigc.hr.common.infrastructure.concurrent.ExecutorNames;

class ModuleBoundaryArchitectureTest {

  private static final Set<String> SPECIAL_SQL_ENFORCED_MODULES =
      Set.of("identity", "intelligence", "recruiting", "reporting", "talent");

  private static final Pattern SELECT_WILDCARD =
      Pattern.compile("(?is)\\bselect\\s+(?:distinct\\s+)?(?:[a-z_][a-z0-9_]*\\.)?\\*\\s+from\\b");

  private final JavaClasses productionClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.ezaigc.hr");

  @Test
  void coarseModuleDependencyGraphHasNoCycles() {
    slices()
        .matching("com.ezaigc.hr.(*)..")
        .should()
        .beFreeOfCycles()
        .because("粗粒度 Maven 模块及组合根必须形成无环依赖图")
        .check(productionClasses);
  }

  @Test
  void commonDoesNotDependOnBusinessOrRuntimeModules() {
    noClasses()
        .that()
        .resideInAPackage("com.ezaigc.hr.common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.ezaigc.hr.identity..",
            "com.ezaigc.hr.talent..",
            "com.ezaigc.hr.recruiting..",
            "com.ezaigc.hr.intelligence..",
            "com.ezaigc.hr.integration..",
            "com.ezaigc.hr.reporting..",
            "com.ezaigc.hr.bootstrap..")
        .check(productionClasses);
  }

  @Test
  void commonUsesThreeTopLevelCapabilityGroups() {
    Set<String> allowedGroups = Set.of("core", "infrastructure", "web");
    String commonPrefix = "com.ezaigc.hr.common.";
    List<String> violations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.getPackageName().startsWith(commonPrefix))
            .filter(
                javaClass -> {
                  String relativePackage =
                      javaClass.getPackageName().substring(commonPrefix.length());
                  String topLevelGroup = relativePackage.split("\\.", 2)[0];
                  return !allowedGroups.contains(topLevelGroup);
                })
            .map(javaClass -> javaClass.getName() + " -> " + javaClass.getPackageName())
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "common 只能使用 core、infrastructure、web 三个一级能力分组:\n" + String.join("\n", violations));
  }

  @Test
  void businessModulesDoNotDependOnCompositionRoots() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "com.ezaigc.hr.identity..",
            "com.ezaigc.hr.talent..",
            "com.ezaigc.hr.recruiting..",
            "com.ezaigc.hr.intelligence..",
            "com.ezaigc.hr.integration..",
            "com.ezaigc.hr.reporting..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.ezaigc.hr.bootstrap..")
        .check(productionClasses);
  }

  @Test
  void springComponentsUseResourceInjectionInsteadOfAutowiredOrInjectionConstructors() {
    noFields().should().beAnnotatedWith(Autowired.class).check(productionClasses);

    List<String> constructorViolations =
        productionClasses.stream()
            .filter(
                javaClass ->
                    javaClass.isAnnotatedWith(Component.class)
                        || javaClass.isAnnotatedWith(Service.class)
                        || javaClass.isAnnotatedWith(Controller.class)
                        || javaClass.isAnnotatedWith(RestController.class)
                        || javaClass.isAnnotatedWith(Configuration.class))
            .flatMap(javaClass -> javaClass.getConstructors().stream())
            .filter(constructor -> !constructor.getRawParameterTypes().isEmpty())
            .map(constructor -> constructor.getFullName())
            .toList();

    assertTrue(
        constructorViolations.isEmpty(),
        () ->
            "Spring 托管组件禁止依赖注入构造器，统一使用 jakarta.annotation.Resource:\n"
                + String.join("\n", constructorViolations));

    List<String> resourceMethodViolations =
        productionClasses.stream()
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(Resource.class))
            .map(method -> method.getFullName())
            .toList();

    assertTrue(
        resourceMethodViolations.isEmpty(),
        () -> "@Resource 只能用于依赖字段，禁止误标业务方法:\n" + String.join("\n", resourceMethodViolations));
  }

  @Test
  void scheduledMethodsDoNotCombineFixedDelayWithAsyncProxying() {
    List<String> violations =
        productionClasses.stream()
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(Scheduled.class))
            .filter(method -> method.isAnnotatedWith(Async.class))
            .map(method -> method.getFullName())
            .sorted()
            .toList();

    assertTrue(
        violations.isEmpty(),
        () ->
            "@Scheduled 与 @Async 组合会让 fixedDelay 在异步任务完成前重复入队；应在调度线程合并重叠触发后再提交专用线程池:\n"
                + String.join("\n", violations));
  }

  @Test
  void persistenceImplementationsDoNotBypassOrChainEntityServices() {
    List<String> repositoryResourceViolations =
        productionClasses.stream()
            .flatMap(javaClass -> javaClass.getFields().stream())
            .filter(field -> field.isAnnotatedWith(Resource.class))
            .filter(field -> field.getRawType().getName().equals(IRepository.class.getName()))
            .map(field -> field.getFullName() + " -> " + field.getRawType().getName())
            .toList();

    assertTrue(
        repositoryResourceViolations.isEmpty(),
        () ->
            "禁止通过 @Resource 直接注入裸 IRepository，实体依赖必须声明为对应 IService 接口:\n"
                + String.join("\n", repositoryResourceViolations));

    List<String> mapperResourceViolations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.getPackageName().contains(".service.impl"))
            .flatMap(javaClass -> javaClass.getFields().stream())
            .filter(field -> field.isAnnotatedWith(Resource.class))
            .filter(field -> field.getRawType().getPackageName().contains(".mapper"))
            .map(field -> field.getFullName() + " -> " + field.getRawType().getName())
            .toList();

    assertTrue(
        mapperResourceViolations.isEmpty(),
        () ->
            "持久化 Service 禁止直接注入辅助 Mapper；每个实体必须由自己的 ServiceImpl 管理:\n"
                + String.join("\n", mapperResourceViolations));

    List<String> persistenceServiceChainingViolations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.getPackageName().contains(".service.impl"))
            .flatMap(javaClass -> javaClass.getFields().stream())
            .filter(field -> field.isAnnotatedWith(Resource.class))
            .filter(field -> field.getRawType().isAssignableTo(IService.class))
            .map(field -> field.getFullName() + " -> " + field.getRawType().getName())
            .toList();

    assertTrue(
        persistenceServiceChainingViolations.isEmpty(),
        () ->
            "持久化 ServiceImpl 禁止编排其他实体 Service，多个实体 Service 必须由 Manager 调用:\n"
                + String.join("\n", persistenceServiceChainingViolations));
  }

  @Test
  void productionCodeDoesNotUseStaticDbRepositoryLookup() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("com.baomidou.mybatisplus.extension.toolkit.Db")
        .because("静态 Db 会隐藏持久化依赖；每个实体必须通过自己的 IService/ServiceImpl 访问")
        .check(productionClasses);
  }

  @Test
  void eachPersistenceEntityHasExactlyOneMapperServiceAndImplementation() {
    Map<String, List<String>> mappersByEntity = new TreeMap<>();
    Map<String, List<String>> servicesByEntity = new TreeMap<>();
    Map<String, List<String>> implementationsByEntity = new TreeMap<>();

    productionClasses.stream()
        .filter(ModuleBoundaryArchitectureTest::isOwnedPersistenceClass)
        .forEach(
            javaClass -> {
              List<JavaType> mapperArguments =
                  directGenericArguments(javaClass, BaseMapper.class.getName());
              if (mapperArguments.size() == 1) {
                addTypeOwner(mappersByEntity, mapperArguments.getFirst(), javaClass.getName());
              }

              List<JavaType> serviceArguments =
                  directGenericArguments(javaClass, IService.class.getName());
              if (serviceArguments.size() == 1) {
                addTypeOwner(servicesByEntity, serviceArguments.getFirst(), javaClass.getName());
              }

              List<JavaType> implementationArguments =
                  directGenericArguments(javaClass, ServiceImpl.class.getName());
              if (implementationArguments.size() == 2) {
                String entityName = implementationArguments.get(1).toErasure().getName();
                String mapperName = implementationArguments.getFirst().toErasure().getName();
                implementationsByEntity
                    .computeIfAbsent(entityName, ignored -> new ArrayList<>())
                    .add(javaClass.getName() + " -> " + mapperName);
              }
            });

    Set<String> entities = new LinkedHashSet<>();
    productionClasses.stream()
        .filter(ModuleBoundaryArchitectureTest::isOwnedPersistenceClass)
        .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
        .map(JavaClass::getName)
        .forEach(entities::add);
    entities.addAll(mappersByEntity.keySet());
    entities.addAll(servicesByEntity.keySet());
    entities.addAll(implementationsByEntity.keySet());
    List<String> violations = new ArrayList<>();
    for (String entity : entities) {
      List<String> mappers = mappersByEntity.getOrDefault(entity, List.of());
      List<String> services = servicesByEntity.getOrDefault(entity, List.of());
      List<String> implementations = implementationsByEntity.getOrDefault(entity, List.of());
      if (mappers.size() != 1 || services.size() != 1 || implementations.size() != 1) {
        violations.add(
            entity + " mapper=" + mappers + ", service=" + services + ", impl=" + implementations);
        continue;
      }
      String expectedMapper = mappers.getFirst();
      if (!implementations.getFirst().endsWith(" -> " + expectedMapper)) {
        violations.add(
            entity
                + " 的 ServiceImpl 使用了错误 Mapper: "
                + implementations.getFirst()
                + ", expected="
                + expectedMapper);
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "持久化结构必须保持 Entity -> BaseMapper -> IService -> ServiceImpl 一一对应:\n"
                + String.join("\n", violations));
  }

  private static boolean isOwnedPersistenceClass(JavaClass javaClass) {
    return Stream.of("identity", "talent", "recruiting", "reporting", "intelligence")
        .anyMatch(module -> javaClass.getPackageName().startsWith("com.ezaigc.hr." + module + "."));
  }

  private static List<JavaType> directGenericArguments(JavaClass javaClass, String rawTypeName) {
    return Stream.concat(javaClass.getInterfaces().stream(), javaClass.getSuperclass().stream())
        .filter(type -> type.toErasure().getName().equals(rawTypeName))
        .filter(JavaParameterizedType.class::isInstance)
        .map(JavaParameterizedType.class::cast)
        .map(JavaParameterizedType::getActualTypeArguments)
        .findFirst()
        .orElse(List.of());
  }

  private static void addTypeOwner(
      Map<String, List<String>> ownersByEntity, JavaType entityType, String ownerName) {
    ownersByEntity
        .computeIfAbsent(entityType.toErasure().getName(), ignored -> new ArrayList<>())
        .add(ownerName);
  }

  @Test
  void mapperQueriesDeclareTheirResultColumns() {
    List<String> violations = new ArrayList<>();
    productionClasses.stream()
        .flatMap(javaClass -> javaClass.getMethods().stream())
        .filter(method -> method.isAnnotatedWith(Select.class))
        .forEach(
            method -> {
              for (String statement : method.getAnnotationOfType(Select.class).value()) {
                if (SELECT_WILDCARD.matcher(statement).find()) {
                  violations.add(
                      method.getFullName() + " -> " + statement.replaceAll("\\s+", " ").trim());
                }
              }
            });

    assertTrue(
        violations.isEmpty(),
        () -> "MyBatis 查询必须显式声明结果列，禁止 SELECT * 或 alias.*:\n" + String.join("\n", violations));
  }

  @Test
  void auditedMapperModulesKeepOnlyDocumentedSpecialSql() {
    List<String> missingReasons = new ArrayList<>();
    List<String> invalidMarkers = new ArrayList<>();

    productionClasses.stream()
        .flatMap(javaClass -> javaClass.getMethods().stream())
        .forEach(
            method -> {
              boolean mapperSql =
                  method.isAnnotatedWith(Select.class)
                      || method.isAnnotatedWith(Insert.class)
                      || method.isAnnotatedWith(Update.class)
                      || method.isAnnotatedWith(Delete.class);
              boolean auditedModule =
                  SPECIAL_SQL_ENFORCED_MODULES.stream()
                      .anyMatch(
                          module ->
                              method
                                      .getOwner()
                                      .getPackageName()
                                      .startsWith("com.ezaigc.hr." + module + ".")
                                  && method.getOwner().getPackageName().contains(".mapper"));
              if (auditedModule && mapperSql && !method.isAnnotatedWith(SpecialSql.class)) {
                missingReasons.add(method.getFullName());
              }
              if (method.isAnnotatedWith(SpecialSql.class)) {
                String reason = method.getAnnotationOfType(SpecialSql.class).value();
                if (!mapperSql
                    || !method.getOwner().isAnnotatedWith(Mapper.class)
                    || reason.isBlank()) {
                  invalidMarkers.add(method.getFullName());
                }
              }
            });

    assertTrue(
        missingReasons.isEmpty(),
        () -> "已完成 Mapper 审计的模块只允许带非空原因的 @SpecialSql:\n" + String.join("\n", missingReasons));
    assertTrue(
        invalidMarkers.isEmpty(),
        () -> "@SpecialSql 只能标记 @Mapper 中的真实注解 SQL:\n" + String.join("\n", invalidMarkers));
  }

  @Test
  void persistentVersionFieldsEnableMybatisPlusOptimisticLocking() {
    List<String> violations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
            .flatMap(javaClass -> javaClass.getFields().stream())
            .filter(field -> field.getName().equals("version"))
            .filter(field -> !field.isAnnotatedWith(Version.class))
            .map(field -> field.getFullName() + " -> 缺少 @Version")
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "持久化实体的 version 字段必须启用 MyBatis-Plus @Version:\n" + String.join("\n", violations));
  }

  @Test
  void persistentEntitiesDeclareAtMostOneOptimisticLockFieldAcrossTheirHierarchy() {
    List<String> violations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(TableName.class))
            .filter(
                javaClass ->
                    javaClass.getAllFields().stream()
                            .filter(field -> field.isAnnotatedWith(Version.class))
                            .count()
                        > 1)
            .map(javaClass -> javaClass.getName() + " -> 存在多个 @Version 字段")
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "每个持久化实体的继承层级只能声明一个 MyBatis-Plus @Version 字段:\n" + String.join("\n", violations));
  }

  @Test
  void productionConfigurationUsesTypedPropertiesInsteadOfValueInjection() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(Value.class.getName())
        .because("生产配置必须集中在带校验的 @ConfigurationProperties 中")
        .check(productionClasses);
  }

  @Test
  void scheduledWorkersUseKnownWorkloadExecutors() {
    Set<String> executorNames =
        Set.of(
            ExecutorNames.AI,
            ExecutorNames.MATCHING,
            ExecutorNames.SEARCH,
            ExecutorNames.OUTBOX,
              ExecutorNames.REPORTING,
              ExecutorNames.DIRECTORY,
            ExecutorNames.MAIL,
            ExecutorNames.MAINTENANCE);
    List<String> violations =
        productionClasses.stream()
            .filter(
                javaClass ->
                    javaClass.getMethods().stream()
                        .anyMatch(method -> method.isAnnotatedWith(Scheduled.class)))
            .filter(
                javaClass -> {
                  long scheduledMethodCount =
                      javaClass.getMethods().stream()
                          .filter(method -> method.isAnnotatedWith(Scheduled.class))
                          .count();
                  long dispatcherCount =
                      javaClass.getFields().stream()
                          .filter(
                              field ->
                                  field
                                      .getRawType()
                                      .getName()
                                      .equals(CoalescingTaskDispatcher.class.getName()))
                          .count();
                  boolean hasKnownExecutor =
                      javaClass.getFields().stream()
                          .filter(field -> field.getRawType().getName().equals(TaskExecutor.class.getName()))
                          .filter(field -> field.isAnnotatedWith(Resource.class))
                          .anyMatch(
                              field ->
                                  executorNames.contains(
                                      field.getAnnotationOfType(Resource.class).name()));
                  return dispatcherCount < scheduledMethodCount || !hasKnownExecutor;
                })
            .map(javaClass -> javaClass.getName() + " -> 缺少逐任务合并器或已登记的专用执行器")
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "定时 Worker 必须把业务负载提交到统一管理的专用有界线程池:\n" + String.join("\n", violations));
  }

  @Test
  void transactionalMethodsArePublicProxyEntryPoints() {
    List<String> violations =
        productionClasses.stream()
            .flatMap(javaClass -> javaClass.getMethods().stream())
            .filter(method -> method.isAnnotatedWith(Transactional.class))
            .filter(method -> !method.getModifiers().contains(JavaModifier.PUBLIC))
            .map(method -> method.getFullName() + " -> 非 public 方法无法通过 Spring 代理获得事务语义")
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "@Transactional 只能标记可由 Spring 代理拦截的 public 方法:\n" + String.join("\n", violations));
  }

  @Test
  void businessControllersUseTheControllerLayerAndDoNotReachMappers() {
    List<String> placementViolations =
        productionClasses.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
            .filter(
                javaClass -> !javaClass.getPackageName().startsWith("com.ezaigc.hr.bootstrap.web"))
            .filter(javaClass -> !javaClass.getPackageName().contains(".controller"))
            .map(javaClass -> javaClass.getName() + " -> " + javaClass.getPackageName())
            .toList();

    assertTrue(
        placementViolations.isEmpty(),
        () -> "业务 REST Controller 必须位于 controller 层:\n" + String.join("\n", placementViolations));

    noClasses()
        .that()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..mapper..", "..domain.entity..", "..service.impl..")
        .because("Controller 只能调用应用层、Service 接口和 HTTP 模型，不能接触 Mapper、Entity 或实现类")
        .check(productionClasses);
  }

  @Test
  void httpFlowUsesManagersWithoutSkippingLayersInEveryBusinessModule() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .and()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..application.manager..")
        .because("每个业务 Controller 都必须把 HTTP 用例委派给 application.manager")
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage("..controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..service..")
        .because("Controller 必须通过 application.manager 调用用例，不能直连持久化 Service")
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..mapper..")
        .because("应用层只能依赖 Service 接口，不能越过持久化 Service 直连 Mapper")
        .check(productionClasses);

    classes()
        .that()
        .resideInAPackage("..application.manager..")
        .and()
        .areTopLevelClasses()
        .and()
        .haveSimpleNameEndingWith("Manager")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..service", "..application.provider")
        .because("Manager 必须通过 Service 访问持久化能力，或通过 Provider Port 访问外部能力")
        .check(productionClasses);
  }

  @Test
  void vagueLegacyLayerPackagesAndRecordContainersAreForbidden() {
    Pattern vagueBusinessPackage =
        Pattern.compile(
            "com\\.ezaigc\\.hr\\.(?:identity|talent|reporting|recruiting\\.(?:demand|recruitment|interview|submission)|intelligence\\.(?:matching|search))\\.(?:web|core|catalog|parsing|run|configuration|intake|risk|engagement|projection|query|template|audit|export|metric|task|threshold|account|access|administration|security)(?:\\..*)?");
    List<String> violations =
        productionClasses.stream()
            .filter(
                javaClass ->
                    vagueBusinessPackage.matcher(javaClass.getPackageName()).matches()
                        || javaClass.getSimpleName().endsWith("Records"))
            .map(javaClass -> javaClass.getName() + " -> " + javaClass.getPackageName())
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "禁止含义模糊的业务分层包和 *Records 聚合容器:\n" + String.join("\n", violations));
  }

  @Test
  void mappersAndConfigurationTypesUseTheirFixedPackages() {
    classes()
        .that()
        .areAnnotatedWith(Mapper.class)
        .should()
        .resideInAPackage("..mapper..")
        .because("所有 MyBatis Mapper 必须集中在 mapper 目录")
        .check(productionClasses);

    classes()
        .that()
        .areAnnotatedWith(Configuration.class)
        .or()
        .areAnnotatedWith(ConfigurationProperties.class)
        .should()
        .resideInAPackage("..config..")
        .because("配置类与类型化配置必须集中在 config 目录")
        .check(productionClasses);
  }

  @Test
  void constantsUseDedicatedPackagesAndUtilityClasses() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Constants")
        .should()
        .resideInAPackage("..constant")
        .andShould()
        .haveModifier(JavaModifier.FINAL)
        .because("稳定常量必须集中在 constant 目录的 final *Constants 工具类")
        .check(productionClasses);

    classes()
        .that()
        .resideInAPackage("..constant")
        .should()
        .haveSimpleNameEndingWith("Constants")
        .because("constant 目录只承载职责明确的 *Constants 常量类")
        .check(productionClasses);
  }

  @Test
  void serviceInterfacesAndImplementationsUseTheSpringAiLayout() {
    classes()
        .that()
        .resideInAPackage("..service")
        .and()
        .haveSimpleNameEndingWith("Service")
        .should()
        .beInterfaces()
        .because("service 根目录只允许 Service 接口")
        .check(productionClasses);

    classes()
        .that()
        .resideInAPackage("..service")
        .and()
        .haveSimpleNameEndingWith("Service")
        .should()
        .beAssignableTo(IService.class)
        .because("持久化 Service 接口必须统一继承 MyBatis-Plus IService")
        .check(productionClasses);

    classes()
        .that()
        .resideInAPackage("..service.impl")
        .and()
        .areTopLevelClasses()
        .should()
        .haveSimpleNameEndingWith("ServiceImpl")
        .because("service.impl 中的实现统一使用 ServiceImpl 后缀")
        .check(productionClasses);

    classes()
        .that()
        .resideInAPackage("..service.impl")
        .and()
        .areTopLevelClasses()
        .should()
        .beAssignableTo(ServiceImpl.class)
        .because("service.impl 只允许 MyBatis-Plus 薄持久化实现；业务逻辑必须位于 Manager")
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage("..service.impl..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..application..",
            "..controller..",
            "..domain.service..",
            "org.springframework.transaction..")
        .because("ServiceImpl 只能实现持久化，不得反向承载应用编排、HTTP、事务或领域服务逻辑")
        .check(productionClasses);

    noClasses()
        .that()
        .resideOutsideOfPackage("..service.impl..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..service.impl..")
        .because("Controller、Manager、Worker 和配置只能依赖 Service 接口，不能反向绑定实现类")
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage("..service..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.springframework.web.multipart.MultipartFile")
        .because("Web 上传类型必须在 Controller/Assembler 边界转换为 DTO")
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage("..service.impl..")
        .and()
        .resideOutsideOfPackage("com.ezaigc.hr.identity..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.ezaigc.hr.identity..")
        .because("权限判断属于 Manager，不属于持久化 ServiceImpl")
        .check(productionClasses);
  }

  @Test
  void applicationProviderContainsOnlyTrueExternalPortInterfaces() {
    classes()
        .that()
        .resideInAPackage("..application.provider")
        .and()
        .areTopLevelClasses()
        .should()
        .beInterfaces()
        .because("application.provider 只定义真实外部能力 Port；同 JVM 模块能力必须由 service/impl 实现")
        .check(productionClasses);
  }

  @Test
  void controllerModelsAreTopLevelClasses() {
    List<String> nestedControllerModels =
        productionClasses.stream()
            .filter(javaClass -> javaClass.getName().contains("$"))
            .filter(javaClass -> javaClass.getPackageName().contains(".controller"))
            .map(JavaClass::getName)
            .toList();

    assertTrue(
        nestedControllerModels.isEmpty(),
        () ->
            "Controller 内禁止声明 Request、Response 或其他嵌套模型:\n"
                + String.join("\n", nestedControllerModels));
  }

  @Test
  void productionCodeUsesOrdinaryClassesInsteadOfRecords() {
    List<String> violations =
        productionClasses.stream()
            .filter(JavaClass::isRecord)
            .map(JavaClass::getName)
            .toList();

    assertTrue(
        violations.isEmpty(),
        () -> "生产代码统一使用职责明确的普通类，禁止 Java record:\n" + String.join("\n", violations));
  }

  @Test
  void enumsUseStableEnglishCodesAndChineseDescriptions() {
    List<String> violations = new ArrayList<>();
    productionClasses.stream()
        .filter(JavaClass::isEnum)
        .forEach(
            javaClass -> {
              if (javaClass.getName().contains("$")
                  || !javaClass.getPackageName().contains(".enums")) {
                violations.add(javaClass.getName() + " -> 枚举必须是 enums 包中的顶级类型");
                return;
              }
              try {
                Class<?> type = Class.forName(javaClass.getName());
                if (!CodeDescEnum.class.isAssignableFrom(type)) {
                  violations.add(javaClass.getName() + " -> 未实现 CodeDescEnum");
                  return;
                }
                for (Object constant : type.getEnumConstants()) {
                  CodeDescEnum value = (CodeDescEnum) constant;
                  String englishCode = ((Enum<?>) constant).name();
                  if (!englishCode.equals(value.getCode())) {
                    violations.add(javaClass.getName() + "." + englishCode + " -> code 必须与英文枚举名一致");
                  }
                  if (value.getDesc() == null
                      || value.getDesc().isBlank()
                      || value.getDesc().chars().noneMatch(character -> character > 127)) {
                    violations.add(javaClass.getName() + "." + englishCode + " -> desc 必须是非空中文说明");
                  }
                }
              } catch (ClassNotFoundException exception) {
                violations.add(javaClass.getName() + " -> 无法加载枚举类型");
              }
            });

    assertTrue(
        violations.isEmpty(),
        () -> "枚举必须同时提供稳定英文 code 和中文 desc:\n" + String.join("\n", violations));
  }

  @Test
  void assemblersAreInjectableComponents() {
    classes()
        .that()
        .resideInAPackage("..application.assembler..")
        .and()
        .areTopLevelClasses()
        .should()
        .beAnnotatedWith(Component.class)
        .because("Assembler 统一由 Spring 管理并通过 @Resource 注入")
        .check(productionClasses);
  }

  @Test
  void standardQueriesUseLambdaWrappersInsteadOfStringColumnQueryWrapper() {
    noClasses()
        .that()
        .resideInAPackage("..service.impl..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.QueryWrapper")
        .because("普通条件查询必须使用 Wrappers.lambdaQuery/LambdaQueryWrapper 与实体 getter")
        .check(productionClasses);
  }
}

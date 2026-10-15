package com.ezaigc.hr.architecture;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** 使用 JDK 解析器检查源码中的类型引用，字符串、文本块和注释不参与检查。 */
public final class JavaImportPolicy {
  private static final Pattern QUALIFIED_TYPE =
      Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");

  private JavaImportPolicy() {}

  record Reference(String name, int start, int end, long line) {
    String simpleName() {
      return name.substring(name.lastIndexOf('.') + 1);
    }
  }

  private record Edit(int start, int end, String replacement) {}

  record Source(Path path, String text, String packageName, int importEnd,
                boolean hasImports, Map<String, String> imports,
                List<Reference> violations, List<Reference> collisions) {
    String normalized() {
      Map<String, String> selected = new HashMap<>(imports);
      Set<String> additions = new TreeSet<>();
      List<Reference> replacements = new ArrayList<>();
      for (Reference reference : violations) {
        String simple = reference.simpleName();
        String bound = selected.putIfAbsent(simple, reference.name());
        if (bound != null && !bound.equals(reference.name())) {
          continue;
        }
        replacements.add(reference);
        String owner = reference.name().substring(0, reference.name().lastIndexOf('.'));
        if (bound == null && !owner.equals(packageName) && !owner.equals("java.lang")) {
          additions.add(reference.name());
        }
      }
      List<Edit> edits = new ArrayList<>();
      for (Reference reference : replacements) {
        edits.add(new Edit(reference.start(), reference.end(), reference.simpleName()));
      }
      if (!additions.isEmpty()) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder block = new StringBuilder(hasImports ? newline : newline + newline);
        for (String addition : additions) {
          block.append("import ").append(addition).append(';').append(newline);
        }
        edits.add(new Edit(importEnd, importEnd, block.toString().stripTrailing()));
      }
      StringBuilder result = new StringBuilder(text);
      edits.sort(Comparator.comparingInt(Edit::start).reversed());
      for (Edit edit : edits) {
        result.replace(edit.start(), edit.end(), edit.replacement());
      }
      return result.toString();
    }
  }

  static List<Source> scan(Map<Path, String> sources) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("Java 导入门禁必须使用 JDK 21 执行");
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Map<URI, Path> paths = new HashMap<>();
    List<JavaFileObject> inputs = new ArrayList<>();
    sources.forEach((path, source) -> {
      URI uri = path.toAbsolutePath().toUri();
      paths.put(uri, path);
      inputs.add(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return source;
        }
      });
    });
    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      JavacTask task = (JavacTask) compiler.getTask(
          null, fileManager, diagnostics, List.of("-proc:none", "--release", "21"), null, inputs);
      List<CompilationUnitTree> units = new ArrayList<>();
      task.parse().forEach(units::add);
      var errors = diagnostics.getDiagnostics().stream()
          .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
      if (!errors.isEmpty()) {
        throw new IllegalStateException("Java 源码解析失败，不能跳过导入检查: " + errors);
      }
      SourcePositions positions = Trees.instance(task).getSourcePositions();
      Set<String> sourceTypes = new HashSet<>();
      for (CompilationUnitTree unit : units) {
        String prefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
        unit.getTypeDecls().stream().filter(ClassTree.class::isInstance)
            .map(ClassTree.class::cast)
            .forEach(type -> sourceTypes.add(prefix + type.getSimpleName()));
      }
      List<Source> result = new ArrayList<>();
      for (CompilationUnitTree unit : units) {
        Path path = paths.get(unit.getSourceFile().toUri());
        result.add(inspect(unit, positions, path, sources.get(path), sourceTypes));
      }
      return result;
    }
  }

  private static Source inspect(
      CompilationUnitTree unit, SourcePositions positions, Path path, String text,
      Set<String> sourceTypes) {
    String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
    Map<String, String> imports = new HashMap<>();
    List<String> wildcardImports = new ArrayList<>();
    for (ImportTree declaration : unit.getImports()) {
      String name = declaration.getQualifiedIdentifier().toString();
      if (name.endsWith(".*")) {
        wildcardImports.add(name.substring(0, name.length() - 1));
      } else {
        imports.put(name.substring(name.lastIndexOf('.') + 1), name);
      }
    }
    Map<String, Set<String>> declaredTypes = new HashMap<>();
    var enclosingTypes = new ArrayDeque<String>();
    Set<String> variables = new HashSet<>();
    List<Reference> references = new ArrayList<>();
    TreeScanner<Void, Void> scanner = new TreeScanner<>() {
      @Override
      public Void visitClass(ClassTree tree, Void unused) {
        String simple = tree.getSimpleName().toString();
        enclosingTypes.addLast(simple);
        declaredTypes.computeIfAbsent(simple, ignored -> new HashSet<>())
            .add(packageName + "." + String.join(".", enclosingTypes));
        super.visitClass(tree, unused);
        enclosingTypes.removeLast();
        return null;
      }

      @Override
      public Void visitTypeParameter(TypeParameterTree tree, Void unused) {
        declaredTypes.computeIfAbsent(tree.getName().toString(), ignored -> new HashSet<>())
            .add("<type-parameter>");
        return super.visitTypeParameter(tree, unused);
      }

      @Override
      public Void visitVariable(VariableTree tree, Void unused) {
        variables.add(tree.getName().toString());
        return super.visitVariable(tree, unused);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
        String name = tree.toString();
        if (QUALIFIED_TYPE.matcher(name).matches()) {
          int start = Math.toIntExact(positions.getStartPosition(unit, tree));
          references.add(new Reference(name, start,
              Math.toIntExact(positions.getEndPosition(unit, tree)), unit.getLineMap().getLineNumber(start)));
        }
        return super.visitMemberSelect(tree, unused);
      }
    };
    // 不扫描 package/import；只扫描真实代码节点，因此不会命中 SQL、反射字符串和 JavaDoc。
    unit.getTypeDecls().forEach(tree -> scanner.scan(tree, null));
    unit.getPackageAnnotations().forEach(tree -> scanner.scan(tree, null));
    List<Reference> violations = new ArrayList<>();
    List<Reference> collisions = new ArrayList<>();
    for (Reference reference : references) {
      String root = reference.name().substring(0, reference.name().indexOf('.'));
      if (root.equals("this") || root.equals("super") || variables.contains(root)) {
        continue;
      }
      String simple = reference.simpleName();
      String imported = imports.get(simple);
      boolean conflict = (imported != null && !imported.equals(reference.name()))
          || declaredTypes.getOrDefault(simple, Set.of()).stream()
              .anyMatch(name -> !name.equals(reference.name()))
          || variables.contains(simple)
          || (imported == null && (
              (!reference.name().equals(packageName + "." + simple)
                  && sourceTypes.contains(packageName + "." + simple))
              || (!reference.name().equals("java.lang." + simple) && loadable("java.lang." + simple))
              || wildcardImports.stream().anyMatch(prefix ->
                  !reference.name().equals(prefix + simple)
                      && (sourceTypes.contains(prefix + simple) || loadable(prefix + simple)))));
      (conflict ? collisions : violations).add(reference);
    }
    int importEnd = unit.getImports().isEmpty()
        ? (unit.getPackage() == null ? 0 : Math.toIntExact(positions.getEndPosition(unit, unit.getPackage())))
        : Math.toIntExact(positions.getEndPosition(unit, unit.getImports().getLast()));
    return new Source(path, text, packageName, importEnd, !unit.getImports().isEmpty(),
        imports, violations, collisions);
  }

  private static boolean loadable(String name) {
    try {
      Class.forName(name, false, JavaImportPolicy.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  static Map<Path, String> repositorySources(Path root) throws IOException {
    Map<Path, String> sources = new LinkedHashMap<>();
    try (var modules = Files.list(root)) {
      for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
        for (String sourceSet : List.of("src/main/java", "src/test/java")) {
          Path sourceRoot = module.resolve(sourceSet);
          if (!Files.isDirectory(sourceRoot)) {
            continue;
          }
          try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
              sources.put(file, Files.readString(file));
            }
          }
        }
      }
    }
    return sources;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !(args[0].equals("--check") || args[0].equals("--fix"))) {
      throw new IllegalArgumentException("Usage: JavaImportPolicy.java --check|--fix <repository-root>");
    }
    Path root = Path.of(args[1]).toAbsolutePath().normalize();
    List<Source> sources = scan(repositorySources(root));
    int count = 0;
    int changed = 0;
    for (Source source : sources) {
      for (Reference reference : source.violations()) {
        System.out.println(root.relativize(source.path()) + ":" + reference.line() + " " + reference.name());
        count++;
      }
      if (args[0].equals("--fix") && !source.violations().isEmpty()) {
        if (!Files.readString(source.path()).equals(source.text())) {
          throw new IllegalStateException("源码已被并发修改，停止覆盖: " + source.path());
        }
        Files.writeString(source.path(), source.normalized());
        changed++;
      }
    }
    System.out.println("Scanned " + sources.size() + " sources; violations=" + count + "; rewritten=" + changed);
    if (args[0].equals("--check") && count != 0) {
      System.exit(1);
    }
  }
}

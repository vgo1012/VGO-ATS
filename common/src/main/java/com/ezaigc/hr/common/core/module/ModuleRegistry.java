package com.ezaigc.hr.common.core.module;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.Comparator;

public final class ModuleRegistry {

    /**
     * 模块列表。
     */
    private final Map<String, ModuleDescriptor> modules;

    public ModuleRegistry(Collection<ModuleDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        Map<String, ModuleDescriptor> discovered = new LinkedHashMap<>();
        descriptors.stream()
                .sorted(Comparator.comparing(ModuleDescriptor::getName))
                .forEach(descriptor -> {
                    ModuleDescriptor previous = discovered.putIfAbsent(descriptor.getName(), descriptor);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Duplicate module descriptor: " + descriptor.getName());
                    }
                });
        this.modules = Collections.unmodifiableMap(discovered);
        validateDependenciesExist();
        validateAcyclic();
    }

    public Set<String> moduleNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(modules.keySet()));
    }

    public void requireModules(Collection<String> requiredModules) {
        Set<String> missing = new TreeSet<>(requiredModules);
        missing.removeAll(modules.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Runtime is missing required modules: " + missing);
        }
    }

    private void validateDependenciesExist() {
        for (ModuleDescriptor descriptor : modules.values()) {
            Set<String> missing = new TreeSet<>(descriptor.getDependencies());
            missing.removeAll(modules.keySet());
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Module " + descriptor.getName() + " is missing dependencies: " + missing);
            }
        }
    }

    private void validateAcyclic() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        for (String module : modules.keySet()) {
            visit(module, visited, visiting, path);
        }
    }

    private void visit(String module, Set<String> visited, Set<String> visiting, ArrayDeque<String> path) {
        if (visited.contains(module)) {
            return;
        }
        if (!visiting.add(module)) {
            path.addLast(module);
            throw new IllegalStateException("Cyclic module dependency: " + String.join(" -> ", path));
        }
        path.addLast(module);
        for (String dependency : modules.get(module).getDependencies()) {
            visit(dependency, visited, visiting, path);
        }
        path.removeLast();
        visiting.remove(module);
        visited.add(module);
    }
}

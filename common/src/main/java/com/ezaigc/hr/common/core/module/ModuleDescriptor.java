package com.ezaigc.hr.common.core.module;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ModuleDescriptor {

    /**
     * 名称。
     */
    private String name;
    /**
     * 模块依赖集合。
     */
    private Set<String> dependencies;

    public ModuleDescriptor(String name, Set<String> dependencies) {
        name = requireText(name, "name");
        dependencies = Set.copyOf(new TreeSet<>(Objects.requireNonNull(dependencies, "dependencies")));
        if (dependencies.contains(name)) {
            throw new IllegalArgumentException("Module cannot depend on itself: " + name);
        }
        this.name = name;
        this.dependencies = dependencies;
    }

    public static ModuleDescriptor module(String name, String... dependencies) {
        return new ModuleDescriptor(name, Set.of(dependencies));
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

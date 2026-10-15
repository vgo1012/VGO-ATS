package com.ezaigc.hr.common.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleRegistryTest {

    @Test
    void exposesModulesInStableOrder() {
        ModuleRegistry registry = new ModuleRegistry(List.of(
                ModuleDescriptor.module("talent"),
                ModuleDescriptor.module("matching", "talent")));

        assertEquals(Set.of("matching", "talent"), registry.moduleNames());
    }

    @Test
    void rejectsMissingDependencies() {
        var descriptor = ModuleDescriptor.module("matching", "talent");

        assertThrows(IllegalStateException.class, () -> new ModuleRegistry(List.of(descriptor)));
    }

    @Test
    void rejectsDependencyCycles() {
        var first = ModuleDescriptor.module("first", "second");
        var second = ModuleDescriptor.module("second", "first");

        assertThrows(IllegalStateException.class, () -> new ModuleRegistry(List.of(first, second)));
    }
}

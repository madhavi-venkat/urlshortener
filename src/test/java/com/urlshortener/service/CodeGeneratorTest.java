package com.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator(7);

    @Test
    void generatesCodeOfConfiguredLength() {
        assertThat(generator.generate()).hasSize(7);
    }

    @Test
    void generatesOnlyBase62Characters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[0-9A-Za-z]+");
        }
    }

    @Test
    void generatesHighlyUniqueCodes() {
        // Not a correctness guarantee (the DB unique constraint is), but a sanity check
        // that the generator isn't pathologically colliding: 10k codes should be ~unique.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSizeGreaterThan(9_990);
    }
}

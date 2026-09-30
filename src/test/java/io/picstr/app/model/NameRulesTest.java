package io.picstr.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NameRulesTest {

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(NameRules.normalize("Tag", "  UK ")).isEqualTo("uk");
    }

    @Test
    void normalize_rejectsNamesShorterThanTwoCharactersAfterTrimming() {
        assertThatThrownBy(() -> NameRules.normalize("Tag", " a "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Tag name must be between 2 and 100 characters");
    }

    @Test
    void normalize_rejectsNamesLongerThan100Characters() {
        assertThatThrownBy(() -> NameRules.normalize("Category", "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Category name must be between");
    }

    @Test
    void normalize_rejectsBlankNames() {
        assertThatThrownBy(() -> NameRules.normalize("Category", "   "))
                .hasMessage("Category name is required");
    }
}

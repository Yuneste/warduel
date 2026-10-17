package com.warduel.warduel.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Yuko                | Yuko",
            "'  Kai   the  Great '| Kai the Great",
            "Zoë-Marie           | Zoë-Marie",
            "李小龙               | 李小龙",
            "o'neil.jr_2         | o'neil.jr_2",
            "<b>bold</b>         | bboldb",
    })
    void keepsReadableNamesAndRemovesTheRest(String raw, String expected) {
        assertThat(PlayerNames.sanitize(raw)).isEqualTo(expected);
    }

    @Test
    void tabsAndLineBreaksBecomeSingleSpaces() {
        assertThat(PlayerNames.sanitize("a\tb\n\nc")).isEqualTo("a b c");
    }

    @Test
    void emptyOrUnusableNamesBecomeNull() {
        assertThat(PlayerNames.sanitize(null)).isNull();
        assertThat(PlayerNames.sanitize("   ")).isNull();
        assertThat(PlayerNames.sanitize("<>{}")).isNull();
    }

    @Test
    void longNamesAreCutToSixteenCharacters() {
        assertThat(PlayerNames.sanitize("abcdefghijklmnopqrstuvwxyz")).hasSize(PlayerNames.MAX_LENGTH);
        // emoji are removed, and the limit counts characters rather than UTF-16 units
        assertThat(PlayerNames.sanitize("😀".repeat(3) + "x".repeat(20))).isEqualTo("x".repeat(16));
        assertThat(PlayerNames.sanitize("李".repeat(20))).isEqualTo("李".repeat(16));
    }
}

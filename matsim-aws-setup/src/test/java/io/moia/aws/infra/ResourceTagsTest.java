package io.moia.aws.infra;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTagsTest {

    @Test
    void parse_blankOrNullYieldsNoTags() {
        assertEquals(Map.of(), ResourceTags.parse(null));
        assertEquals(Map.of(), ResourceTags.parse(""));
        assertEquals(Map.of(), ResourceTags.parse("  "));
    }

    @Test
    void parse_keyValuePairsInOrder() {
        Map<String, String> tags = ResourceTags.parse(" Owner = my-team , Service=matsim,Component=batch ");

        assertEquals(List.of("Owner", "Service", "Component"), List.copyOf(tags.keySet()));
        assertEquals("my-team", tags.get("Owner"));
        assertEquals("matsim", tags.get("Service"));
        assertEquals("batch", tags.get("Component"));
    }

    @Test
    void parse_keepsCaseAndAllowsEmptyValuesAndEqualsSignsInValues() {
        Map<String, String> tags = ResourceTags.parse("owner=a,Owner=b,Empty=,Formula=a=b");

        assertEquals("a", tags.get("owner"));
        assertEquals("b", tags.get("Owner"));
        assertEquals("", tags.get("Empty"));
        assertEquals("a=b", tags.get("Formula"));
    }

    @Test
    void parse_ignoresEmptyEntries() {
        assertEquals(Map.of("Owner", "x"), ResourceTags.parse("Owner=x,,"));
    }

    @Test
    void parse_rejectsInvalidEntries() {
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("Owner"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("=value"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("aws:createdBy=me"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("Owner=a,Owner=b"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("k".repeat(129) + "=v"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("Owner=" + "v".repeat(257)));
    }

    @Test
    void validate_allowsTheAwsCharacterSet() {
        assertDoesNotThrow(() -> ResourceTags.validate("Owner", "Mobility Consulting"));
        assertDoesNotThrow(() -> ResourceTags.validate("team:project/sub-part_1.2", "a+b=c@d"));
        assertDoesNotThrow(() -> ResourceTags.validate("Straße", "Zürich 2026"));
    }

    @Test
    void validate_rejectsCharactersOutsideTheAwsCharacterSet() {
        for (String invalid : List.of("Owner#1", "a;b", "a*b", "a\"b", "a\tb", "a\uD83D\uDE00")) {
            assertThrows(IllegalArgumentException.class, () -> ResourceTags.validate(invalid, "value"), invalid);
            assertThrows(IllegalArgumentException.class, () -> ResourceTags.validate("key", invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.parse("Owner=my#team"));
    }

    @Test
    void validate_countsCharactersNotUtf16Units() {
        // U+1D538 (mathematical double-struck A) is a letter outside the BMP: one character, two UTF-16 units
        String letter = new String(Character.toChars(0x1D538));
        assertDoesNotThrow(() -> ResourceTags.validate(letter.repeat(128), letter.repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.validate(letter.repeat(129), "v"));
        assertThrows(IllegalArgumentException.class, () -> ResourceTags.validate("k", letter.repeat(257)));
    }
}

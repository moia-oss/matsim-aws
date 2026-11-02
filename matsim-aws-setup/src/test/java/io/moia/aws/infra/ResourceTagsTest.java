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
}

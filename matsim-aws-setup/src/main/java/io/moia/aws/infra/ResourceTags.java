package io.moia.aws.infra;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tags that are added to AWS resources, e.g. for cost allocation.
 * <p>
 * Tag keys and values are case-sensitive. Whether a tag shows up in cost reports depends on your AWS organisation:
 * the key has to be activated as a cost allocation tag in the management (payer) account.
 */
public final class ResourceTags {

    private ResourceTags() {}

    /**
     * Parses a comma-separated list of {@code key=value} pairs, e.g. {@code "Owner=my-team,Service=matsim"}.
     * Whitespace around keys and values is ignored. A blank or null input yields no tags.
     *
     * @throws IllegalArgumentException if an entry has no {@code =}, an empty or invalid key, or a duplicate key
     */
    public static Map<String, String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Map.of();
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (String entry : csv.split(",")) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("Tag must be given as key=value: '" + entry.strip() + "'");
            }
            String key = entry.substring(0, separator).strip();
            String value = entry.substring(separator + 1).strip();
            validate(key, value);
            if (tags.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate tag key: '" + key + "'");
            }
        }
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Checks a single tag against the AWS tag restrictions (key 1-128 characters, value up to 256 characters,
     * no reserved {@code aws:} prefix).
     *
     * @throws IllegalArgumentException if the tag is invalid
     */
    public static void validate(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Tag key must not be empty");
        }
        if (key.length() > 128) {
            throw new IllegalArgumentException("Tag key must not be longer than 128 characters: '" + key + "'");
        }
        if (key.toLowerCase().startsWith("aws:")) {
            throw new IllegalArgumentException("Tag keys starting with 'aws:' are reserved: '" + key + "'");
        }
        if (value == null) {
            throw new IllegalArgumentException("Tag value must not be null for key '" + key + "'");
        }
        if (value.length() > 256) {
            throw new IllegalArgumentException("Tag value must not be longer than 256 characters for key '" + key + "'");
        }
    }
}

package io.moia.aws.infra;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tags that are added to AWS resources, e.g. for cost allocation.
 * <p>
 * Tag keys and values are case-sensitive. Whether a tag shows up in cost reports depends on your AWS organisation:
 * the key has to be activated as a cost allocation tag in the management (payer) account.
 */
public final class ResourceTags {

    /**
     * Characters AWS allows in tag keys and values: letters, digits, spaces and {@code _ . : / = + - @}.
     */
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{Z}\\p{N}_.:/=+\\-@]*");

    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_VALUE_LENGTH = 256;

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
     * Checks a single tag against the AWS tag restrictions: key 1-128 characters, value up to 256 characters
     * (counted as Unicode characters), only letters, digits, spaces and {@code _ . : / = + - @}, no reserved
     * {@code aws:} prefix. This way an invalid tag fails here rather than at deploy or submission time.
     *
     * @throws IllegalArgumentException if the tag is invalid
     */
    public static void validate(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Tag key must not be empty");
        }
        if (characters(key) > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Tag key must not be longer than " + MAX_KEY_LENGTH + " characters: '" + key + "'");
        }
        if (!ALLOWED.matcher(key).matches()) {
            throw new IllegalArgumentException("Tag key contains characters not allowed by AWS (letters, digits, "
                    + "spaces and _ . : / = + - @ only): '" + key + "'");
        }
        if (key.toLowerCase().startsWith("aws:")) {
            throw new IllegalArgumentException("Tag keys starting with 'aws:' are reserved: '" + key + "'");
        }
        if (value == null) {
            throw new IllegalArgumentException("Tag value must not be null for key '" + key + "'");
        }
        if (characters(value) > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("Tag value must not be longer than " + MAX_VALUE_LENGTH
                    + " characters for key '" + key + "'");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException("Tag value for key '" + key + "' contains characters not allowed by "
                    + "AWS (letters, digits, spaces and _ . : / = + - @ only): '" + value + "'");
        }
    }

    /**
     * AWS counts Unicode characters, not UTF-16 code units.
     */
    private static int characters(String s) {
        return s.codePointCount(0, s.length());
    }
}

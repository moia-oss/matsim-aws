package io.moia.aws.run.example.equil;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.batch.model.KeyValuePair;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The example has to match the contract of {@code docker-build/run.sh}: inputs are only downloaded for the entries
 * in {@code INPUT_PATHS}, and each entry keeps its S3 key as local path below the working directory.
 */
class EquilExampleTest {

    private static Map<String, String> environment() {
        return EquilExampleJobDefinition.environment().stream()
                .collect(Collectors.toMap(KeyValuePair::name, KeyValuePair::value));
    }

    @Test
    void jobDefinitionDownloadsTheScenarioDirectoryViaInputPaths() {
        Map<String, String> environment = environment();
        assertEquals(PrepareInput.SCENARIO + "/", environment.get("INPUT_PATHS"),
                "run.sh only downloads INPUT_PATHS; a trailing slash syncs the whole scenario directory");
        assertFalse(environment.containsKey("SCENARIO"), "run.sh no longer reads SCENARIO");
    }

    @Test
    void runEquilReadsTheConfigWhereRunShPlacesTheDownloadedInput() {
        // run.sh syncs a relative entry "examples/equil/" to "./examples/equil/" in the working directory
        String localScenarioDirectory = environment().get("INPUT_PATHS");
        assertEquals(localScenarioDirectory + "config.xml", RunEquil.CONFIG);
    }
}

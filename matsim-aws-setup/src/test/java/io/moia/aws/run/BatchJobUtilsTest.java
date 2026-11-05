package io.moia.aws.run;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.batch.model.KeyValuePair;
import software.amazon.awssdk.services.batch.model.RegisterJobDefinitionRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BatchJobUtilsTest {

    private static BatchJobUtils.JobSubmission submission() {
        return new BatchJobUtils.JobSubmission()
                .jobRecord(new BatchJobUtils.JobRecord("definition", "run", List.of()));
    }

    @Test
    void jobSubmission_tagsArePassedAndPropagated() {
        SubmitJobRequest request = submission()
                .owner("someone")
                .tag("Owner", "my-team")
                .tags(Map.of("Component", "scenario-a"))
                .buildRequest();

        assertEquals(Map.of("OWNER", "someone", "Owner", "my-team", "Component", "scenario-a"), request.tags());
        assertTrue(request.propagateTags());
    }

    @Test
    void jobSubmission_withoutTags() {
        SubmitJobRequest request = submission().buildRequest();

        assertTrue(request.tags().isEmpty());
    }

    @Test
    void jobSubmission_rejectsInvalidTags() {
        assertThrows(IllegalArgumentException.class, () -> submission().tag("aws:reserved", "x"));
        assertThrows(IllegalArgumentException.class, () -> submission().tag("", "x"));
        assertThrows(IllegalArgumentException.class, () -> submission().tag("Component", "a#b"));
    }

    @Test
    void jobSubmission_ownerIsValidatedLikeOtherTags() {
        assertEquals(Map.of("OWNER", "someone"), submission().owner("someone").buildRequest().tags());
        assertThrows(IllegalArgumentException.class, () -> submission().owner("some;one"));
        assertThrows(IllegalArgumentException.class, () -> submission().owner(null));
    }

    @Test
    void jobDefinition_tagsArePassedAndPropagated() {
        RegisterJobDefinitionRequest request = new BatchJobUtils.JobDefinition()
                .definitionName("definition")
                .tag("Owner", "my-team")
                .buildRequest();

        assertEquals(Map.of("Owner", "my-team"), request.tags());
        assertTrue(request.propagateTags());
    }

    @Test
    void jobDefinition_withoutTagsSendsNoTags() {
        RegisterJobDefinitionRequest request = new BatchJobUtils.JobDefinition()
                .definitionName("definition")
                .buildRequest();

        assertFalse(request.hasTags());
    }

    private static Map<String, String> environment(RegisterJobDefinitionRequest request) {
        return request.containerProperties().environment().stream()
                .collect(Collectors.toMap(KeyValuePair::name, KeyValuePair::value));
    }

    private static Map<String, String> environment(SubmitJobRequest request) {
        return request.containerOverrides().environment().stream()
                .collect(Collectors.toMap(KeyValuePair::name, KeyValuePair::value));
    }

    @Test
    void jobDefinition_inputPathsAreSetAsEnvironment() {
        RegisterJobDefinitionRequest request = new BatchJobUtils.JobDefinition()
                .definitionName("definition")
                .inputPaths("scenarios/a/,s3://other-bucket/plans.xml.gz")
                .buildRequest();

        assertEquals("scenarios/a/,s3://other-bucket/plans.xml.gz", environment(request).get("INPUT_PATHS"));
    }

    @Test
    void jobSubmission_inputPathsAreSetAsEnvironment() {
        SubmitJobRequest request = submission().inputPaths("scenarios/a/").buildRequest();

        assertEquals("scenarios/a/", environment(request).get("INPUT_PATHS"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedScenarioSetters_stillOnlySetScenario() {
        // run.sh ignores SCENARIO; the deprecated setters keep their old behaviour and do not touch INPUT_PATHS
        RegisterJobDefinitionRequest definition = new BatchJobUtils.JobDefinition()
                .definitionName("definition")
                .inputScenario("scenarios/a")
                .buildRequest();
        SubmitJobRequest submission = submission().scenario("scenarios/a").buildRequest();

        assertEquals("scenarios/a", environment(definition).get("SCENARIO"));
        assertFalse(environment(definition).containsKey("INPUT_PATHS"));
        assertEquals("scenarios/a", environment(submission).get("SCENARIO"));
        assertFalse(environment(submission).containsKey("INPUT_PATHS"));
    }
}

package io.moia.aws.run;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.batch.model.RegisterJobDefinitionRequest;
import software.amazon.awssdk.services.batch.model.SubmitJobRequest;

import java.util.List;
import java.util.Map;

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
}

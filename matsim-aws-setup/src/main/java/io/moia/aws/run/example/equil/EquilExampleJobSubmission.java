package io.moia.aws.run.example.equil;

import io.moia.aws.run.BatchJobUtils;
import software.amazon.awssdk.regions.Region;

import java.util.List;

public class EquilExampleJobSubmission {

    private final static Region REGION = Region.of(System.getenv("REGION"));

    public static void main(String[] args) {

        BatchJobUtils.JobRecord job = new BatchJobUtils.JobRecord(
                EquilExampleJobDefinition.JOB_DEFINITION_NAME,
                "equil-example",
                List.of("--output", EquilExampleJobDefinition.OUTPUT_SCENARIO)
        );

        new BatchJobUtils.JobSubmission()
                .jobRecord(job)
                .memory(8000)
                .region(REGION)
                .submit();

    }
}

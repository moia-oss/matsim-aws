package io.moia.aws.run.example.equil;

import io.moia.aws.infra.stacks.S3Stack;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class EquilExampleJobDefinition {

    private final static String account = System.getenv("AWS_ACCOUNT");
    private final static String region = System.getenv("REGION");

    public static final String IMAGE = account + ".dkr.ecr." + region + ".amazonaws.com/matsim-jobs-repo:matsim-v0.0.1";
    public final static String JOB_DEFINITION_NAME = "example-equil";

    public static final int MEMORY = 8000;
    public static final int VCPUS = MEMORY / 1000 / 8; // AWS recommends ~ 8Gb per vCPU

    public static final String JOB_ROLE_ARN = "arn:aws:iam::" + account + ":role/MatsimBatchRole";
    public static final String JAR_NAME = "equil.jar";
    public static final String SCENARIO = PrepareInput.SCENARIO;
    public static final String MAIN_CLASS = RunEquil.class.getCanonicalName();

    public static final String OUTPUT_SCENARIO = "examples/equil";

    public static final String INPUT_BUCKET = S3Stack.inputBucketName(account);
    public static final String OUTPUT_BUCKET = S3Stack.outputBucketName(account);

    public static void main(String[] args) {
        BatchClient awsBatch = BatchClient.builder().region(Region.of(region)).build();

        ContainerProperties.Builder containerProperties = ContainerProperties.builder();

        Collection<KeyValuePair> environment = new HashSet<>();
        environment.add(KeyValuePair.builder().name("JAR_NAME").value(JAR_NAME).build());
        environment.add(KeyValuePair.builder().name("SCENARIO").value(SCENARIO).build());
        environment.add(KeyValuePair.builder().name("MAIN_CLASS").value(MAIN_CLASS).build());
        environment.add(KeyValuePair.builder().name("OUTPUT_SCENARIO").value(OUTPUT_SCENARIO).build());
        environment.add(KeyValuePair.builder().name("JOB_INPUT_BUCKET").value(INPUT_BUCKET).build());
        environment.add(KeyValuePair.builder().name("JOB_OUTPUT_BUCKET").value(OUTPUT_BUCKET).build());

        List<ResourceRequirement> resourceRequirements = new ArrayList<>();

        resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.MEMORY).value(String.valueOf(MEMORY)).build());
        resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.VCPU).value(String.valueOf(VCPUS)).build());

        containerProperties
                .image(IMAGE)
                .jobRoleArn(JOB_ROLE_ARN)
                .resourceRequirements(resourceRequirements)
                .environment(environment);

        RegisterJobDefinitionRequest request = RegisterJobDefinitionRequest.builder()
                .jobDefinitionName(JOB_DEFINITION_NAME)
                .type("container")
                .propagateTags(false)
                .platformCapabilities(PlatformCapability.EC2)
                .containerProperties(containerProperties.build()).build();

        RegisterJobDefinitionResponse response = awsBatch.registerJobDefinition(request);
        System.out.println("Submitted job definition: " + response.jobDefinitionName()
                + "\nARN: " + response.jobDefinitionArn() + "\nRevision: " + response.revision());
    }
}

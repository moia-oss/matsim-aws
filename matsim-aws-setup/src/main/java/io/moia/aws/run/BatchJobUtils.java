package io.moia.aws.run;

import io.moia.aws.infra.stacks.BatchStack;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.batch.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class BatchJobUtils {

    private final static S3Client S3 = S3Client.create();

    public static PutObjectResponse uploadFileToS3(String bucket, String key, Path file) {
        PutObjectRequest request = PutObjectRequest
                .builder()
                .bucket(bucket)
                .key(key)
                .build();
        return S3.putObject(request, file);
    }

    public static class JobDefinition {

        private Region region;
        private String jobDefinitionName;
        private String image;
        private String jobRoleARN;

        private final ContainerProperties.Builder containerProperties = ContainerProperties.builder();
        private final Collection<KeyValuePair> environment = new HashSet<>();
        private final List<ResourceRequirement> resourceRequirements = new ArrayList<>();


        public JobDefinition definitionName(String definitionName) {
            this.jobDefinitionName = definitionName;
            return this;
        }

        public JobDefinition memory(int memory) {
            resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.MEMORY).value(String.valueOf(memory)).build());
            return this;
        }

        public JobDefinition vcpu(int cpu) {
            resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.VCPU).value(String.valueOf(cpu)).build());
            return this;
        }

        public JobDefinition jarName(String jar) {
            environment.add(KeyValuePair.builder().name("JAR_NAME").value(jar).build());
            return this;
        }

        public JobDefinition inputBucketName(String inputBucket) {
            environment.add(KeyValuePair.builder().name("JOB_INPUT_BUCKET").value(inputBucket).build());
            return this;
        }

        public JobDefinition outputBucketName(String inputBucket) {
            environment.add(KeyValuePair.builder().name("JOB_OUTPUT_BUCKET").value(inputBucket).build());
            return this;
        }

        public JobDefinition inputScenario(String scenario) {
            environment.add(KeyValuePair.builder().name("SCENARIO").value(scenario).build());
            return this;
        }

        public JobDefinition mainClass(String mainClass) {
            environment.add(KeyValuePair.builder().name("MAIN_CLASS").value(mainClass).build());
            return this;
        }

        public JobDefinition outputScenario(String scenario) {
            environment.add(KeyValuePair.builder().name("OUTPUT_SCENARIO").value(scenario).build());
            return this;
        }

        public JobDefinition image(String dockerImage) {
            this.image = dockerImage;
            return this;
        }

        public JobDefinition region(Region region) {
            this.region = region;
            return this;
        }

        public JobDefinition jobRoleARN(String roleARN) {
            this.jobRoleARN = roleARN;
            return this;
        }

        public void submit() {

            containerProperties
                    .image(image)
                    .jobRoleArn(jobRoleARN)
                    .resourceRequirements(resourceRequirements)
                    .environment(environment);

            BatchClient awsBatch = BatchClient.builder().region(region).build();

            RegisterJobDefinitionRequest request = RegisterJobDefinitionRequest.builder()
                    .jobDefinitionName(jobDefinitionName)
                    .type("container")
                    .propagateTags(true)
                    .platformCapabilities(PlatformCapability.EC2)
                    .containerProperties(containerProperties.build()).build();

            RegisterJobDefinitionResponse response = awsBatch.registerJobDefinition(request);
            System.out.println("Submitted job definition: " + response.jobDefinitionName()
                    + "\nARN: " + response.jobDefinitionArn() + "\nRevision: " + response.revision());
        }
    }


    public record JobRecord(String jobDefinition, String jobName, List<String> args, Integer memory, Integer vcpus) {
        public JobRecord(String jobDefinition, String jobName, List<String> args) {
            this(jobDefinition, jobName, args, null, null);
        }
    }

    public static class JobSubmission {

        private JobRecord jobRecord;
        private Region region;

        private final Collection<KeyValuePair> environment = new HashSet<>();
        private final List<ResourceRequirement> resourceRequirements = new ArrayList<>();

        private final ArrayProperties.Builder arrayPropertiesBuilder = ArrayProperties.builder();


        private final Map<String, String> tags = new HashMap<>();
        private String jobQueue = BatchStack.MATSIM_JOB_QUEUE_ON_DEMAND;

        public JobSubmission memory(int memory) {
            return memory(memory, (int) (memory * 0.95));
        }

        public JobSubmission memory(int memory, int jvmMemory) {
            resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.MEMORY).value(String.valueOf(memory)).build());
            environment.add(KeyValuePair.builder().name("XMX").value(jvmMemory + "M").build());
            return this;
        }

        public JobSubmission vcpus(int vcpus) {
            resourceRequirements.add(ResourceRequirement.builder().type(ResourceType.VCPU).value(String.valueOf(vcpus)).build());
            return this;
        }

        public JobSubmission owner(String owner) {
            tags.put("OWNER", owner);
            return this;
        }

        public JobSubmission debug(boolean debug) {
            environment.add(KeyValuePair.builder().name("DEBUG").value(String.valueOf(debug)).build());
            return this;
        }

        public JobSubmission jobRecord(JobRecord jobRecord) {
            this.jobRecord = jobRecord;
            return this;
        }

        public JobSubmission array(int arraySize) {
            arrayPropertiesBuilder.size(arraySize);
            return this;
        }

        public JobSubmission outputScenario(String outputScenario){
            this.environment.add(KeyValuePair.builder().name("OUTPUT_SCENARIO").value(outputScenario).build());
            return this;
        }

        public JobSubmission scenario(String scenario){
            this.environment.add(KeyValuePair.builder().name("SCENARIO").value(scenario).build());
            return this;
        }

        public JobSubmission jobQueue(String jobQueue) {
            this.jobQueue = jobQueue;
            return this;
        }

        public JobSubmission region(Region region) {
            this.region = region;
            return this;
        }

        public void submit() {

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();

            String jobName = dtf.format(now).concat("-").concat(jobRecord.jobName());

            environment.add(KeyValuePair.builder().name("JOB_NAME").value(jobName).build());

            ContainerOverrides containerOverrides = ContainerOverrides.builder()
                    .environment(environment)
                    .resourceRequirements(resourceRequirements)
                    .command(jobRecord.args()).build();


            ArrayProperties arrayProperties = arrayPropertiesBuilder.build();


            SubmitJobRequest.Builder builder = SubmitJobRequest.builder();
            builder.jobName(jobName)
                    .propagateTags(true)
                    .jobQueue(jobQueue)
                    .tags(tags)
                    .containerOverrides(containerOverrides)
                    .jobDefinition(jobRecord.jobDefinition());

            if (arrayProperties.size() != null && arrayProperties.size() > 1) {
                builder.arrayProperties(arrayProperties);
            }

            SubmitJobRequest request = builder.build();

            BatchClient awsBatch = BatchClient.builder().region(region).build();

            SubmitJobResponse response = awsBatch.submitJob(request);
            System.out.println("Submitted job: " + response.jobName()
                    + "\nID: " + response.jobId() + "\nARN: " + response.jobArn());
            System.out.println("https://" + region + ".console.aws.amazon.com/batch/home?region=" + region + "#jobs/detail/"
                    + response.jobId());
        }
    }
}

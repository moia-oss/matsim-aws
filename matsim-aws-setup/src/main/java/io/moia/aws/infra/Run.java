package io.moia.aws.infra;

import io.moia.aws.infra.stacks.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.List;

public class Run {

    private static final Environment ENV = makeEnv(System.getenv("AWS_ACCOUNT"), System.getenv("REGION"));

    private static final String IAM_POLICY_CSV = System.getenv("IAM_POLICY_CSV");
    private static final boolean DEPLOY_SLACK_LAMBDA = Boolean.parseBoolean(System.getenv("DEPLOY_SLACK_LAMBDA"));
    private static final boolean USE_EXISTING_BUCKETS = Boolean.parseBoolean(System.getenv("USE_EXISTING_BUCKETS"));
    private static final String SLACK_HOOK_URL = System.getenv("SLACK_HOOK_URL");
    private static final String SLACK_CHANNEL_NAME = System.getenv("SLACK_CHANNEL_NAME");

    // Helper method to build an environment
    static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }

    private static void validateBucketsDoNotExist() {
        String account = System.getenv("AWS_ACCOUNT");
        String region = System.getenv("REGION");
        List<String> bucketNames = List.of(S3Stack.inputBucketName(account), S3Stack.outputBucketName(account));
        try (S3Client s3 = S3Client.builder().region(Region.of(region)).build()) {
            for (String bucketName : bucketNames) {
                try {
                    s3.headBucket(r -> r.bucket(bucketName));
                    throw new IllegalStateException(
                            "Bucket '" + bucketName + "' already exists in account " + account + ". " +
                            "Set USE_EXISTING_BUCKETS=true in environment.env to use the existing buckets.");
                } catch (NoSuchBucketException ignored) {
                    // bucket does not exist — safe to create
                } catch (SdkException ignored) {
                    // cannot verify (e.g. insufficient permissions) — skip check and let CloudFormation handle it
                }
            }
        }
    }


    public static void main(final String[] args) {

        if (!USE_EXISTING_BUCKETS) {
            validateBucketsDoNotExist();
        }

        App app = new App();

        StackProps stackProps = StackProps.builder().env(ENV).build();
        VPCStack vpcStack = new VPCStack(app, "VpcStack", stackProps);
        S3Stack s3Stack = new S3Stack(app, "S3Stack", stackProps, USE_EXISTING_BUCKETS);

        IBucket inputBucket = s3Stack.getInputBucket();
        IBucket outputBucket = s3Stack.getOutputBucket();

        new IAMStack(app, "IAMStack", stackProps, inputBucket, outputBucket, PolicyStatementParser.parse(IAM_POLICY_CSV));
        new ECRStack(app, "ECRStack", stackProps);
        new BatchStack(app, "BatchStack", stackProps, vpcStack.getVpc());
        if(DEPLOY_SLACK_LAMBDA) {
            new JobNotificationStack(app, "JobNotificationStack", stackProps, SLACK_HOOK_URL, SLACK_CHANNEL_NAME);
        }
        app.synth();

    }
}

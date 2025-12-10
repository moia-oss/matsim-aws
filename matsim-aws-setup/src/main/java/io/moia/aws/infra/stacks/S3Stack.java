package io.moia.aws.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Optional;


public class S3Stack extends Stack {
    private static String INPUT_BUCKET_ENV_VAR = "INPUT_BUCKET";
    private static String OUTPUT_BUCKET_ENV_VAR = "OUTPUT_BUCKET";

    private static IBucket inputBucket;
    private static IBucket outputBucket;

    public S3Stack(Construct scope, final String name, StackProps stackProps) {
        super(scope, name, stackProps);

        String account = stackProps.getEnv().getAccount();

        inputBucket = System.getenv(INPUT_BUCKET_ENV_VAR) != null ?
                Bucket.fromBucketName(this, "InputBucket", inputBucketName(account)) :
                inputDataBucket(this, account);

        outputBucket = System.getenv(INPUT_BUCKET_ENV_VAR) != null ?
                Bucket.fromBucketName(this, "OutputBucket", outputBucketName(account)) :
                outputDataBucket(this, account);

        // Create CloudFormation outputs for the bucket ARNs
        CfnOutput.Builder.create(this, "outputBucketArn")
                .value(outputBucket.getBucketArn())
                .exportName("outputBucketArn")
                .build();

        CfnOutput.Builder.create(this, "inputBucketArn")
                .value(inputBucket.getBucketArn())
                .exportName("inputBucketArn")
                .build();
    }

    private static final String PREFIX = "matsim-jobs";

    public static String outputBucketName(String account) {
        String fromEnv = System.getenv(OUTPUT_BUCKET_ENV_VAR);
        return fromEnv != null ? fromEnv : PREFIX + "-output-" + account;
    }

    public static String inputBucketName(String account) {
        String fromEnv = System.getenv(INPUT_BUCKET_ENV_VAR);
        return fromEnv != null ? fromEnv : PREFIX + "-input-" + account;
    }

    private static Bucket outputDataBucket(Construct scope, String accountName) {
        String bucketName = outputBucketName(accountName);

        // In AWS CDK, the imported bucket reference is non-null.
        // The following check mimics the original logic.
        LifecycleRule rule1 = LifecycleRule.builder()
                .enabled(true)
                .transitions(Arrays.asList(
                        Transition.builder()
                                .storageClass(StorageClass.INFREQUENT_ACCESS)
                                .transitionAfter(Duration.days(30))
                                .build()
                ))
                .build();

        LifecycleRule rule2 = LifecycleRule.builder()
                .abortIncompleteMultipartUploadAfter(Duration.days(7))
                .build();

        return Bucket.Builder.create(scope, "OutputBucket")
                .bucketName(bucketName)
                .removalPolicy(RemovalPolicy.RETAIN)
                .lifecycleRules(Arrays.asList(rule1, rule2))
                .build();
    }

    private static Bucket inputDataBucket(Construct scope, String stage) {
        String bucketName = inputBucketName(stage);

        LifecycleRule rule1 = LifecycleRule.builder()
                .enabled(true)
                .transitions(Arrays.asList(
                        Transition.builder()
                                .storageClass(StorageClass.INFREQUENT_ACCESS)
                                .transitionAfter(Duration.days(30))
                                .build()
                ))
                .build();

        LifecycleRule rule2 = LifecycleRule.builder()
                .abortIncompleteMultipartUploadAfter(Duration.days(7))
                .build();

        return Bucket.Builder.create(scope, "InputBucket")
                .bucketName(bucketName)
                .versioned(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .lifecycleRules(Arrays.asList(rule1, rule2))
                .build();
    }

    public IBucket getInputBucket() {
        return inputBucket;
    }

    public IBucket getOutputBucket() {
        return outputBucket;
    }
}

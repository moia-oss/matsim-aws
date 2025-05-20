package io.moia.aws.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.util.Arrays;


public class S3Stack extends Stack {

    private static IBucket inputBucket;
    private static IBucket outputBucket;

    public S3Stack(Construct scope, final String name, StackProps stackProps) {
        super(scope, name, stackProps);
        new S3Construct(this, name, stackProps);
    }

    private static class S3Construct extends Construct {


        S3Construct(final Construct parent, final String name, StackProps stackProps) {
            super(parent, name);

          //  boolean useExisting = Boolean.parseBoolean((String) this.getNode().tryGetContext("useExistingBuckets"));
            boolean useExisting = true;

            String account = stackProps.getEnv().getAccount();
            if (useExisting) {
                inputBucket = Bucket.fromBucketName(this, "InputBucket", inputBucketName(account));
                outputBucket = Bucket.fromBucketName(this, "OutputBucket", outputBucketName(account));
            } else {
                outputBucket = outputDataBucket(this, account);
                inputBucket = inputDataBucket(this, account);
            }

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
    }

    private static final String PREFIX = "matsim-jobs";

    public static String outputBucketName(String accountName) {
        return PREFIX + "-output-" + accountName;
    }

    public static String inputBucketName(String accountName) {
        return PREFIX + "-input-" + accountName;
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

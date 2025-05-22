package io.moia.aws.infra;

import io.moia.aws.infra.stacks.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.IBucket;

public class Run {

    private static final Environment ENV = makeEnv(System.getenv("AWS_ACCOUNT"), System.getenv("REGION"));

    private static final boolean DEPLOY_SLACK_LAMBDA = Boolean.parseBoolean(System.getenv("DEPLOY_SLACK_LAMBDA"));
    private static final String SLACK_HOOK_URL = System.getenv("SLACK_HOOK_URL");
    private static final String SLACK_CHANNEL_NAME = System.getenv("SLACK_CHANNEL_NAME");

    // Helper method to build an environment
    static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }


    public static void main(final String[] args) {

        App app = new App();

        StackProps stackProps = StackProps.builder().env(ENV).build();
        VPCStack vpcStack = new VPCStack(app, "VpcStack", stackProps);
        S3Stack s3Stack = new S3Stack(app, "S3Stack", stackProps);

        IBucket inputBucket = s3Stack.getInputBucket();
        IBucket outputBucket = s3Stack.getOutputBucket();

        // add potential policy statements here
        //PolicyStatement policyStatement = new PolicyStatement();
        //policyStatement.setEffect(Effect.ALLOW);
        //policyStatement.addActions("sts:AssumedRole");
        //policyStatement.addResources("arn:aws:iam::...");

        //new IAMStack(app, "IAMStack", stackProps, inputBucket, outputBucket, policyStatement);
        new IAMStack(app, "IAMStack", stackProps, inputBucket, outputBucket);
        new ECRStack(app, "ECRStack", stackProps);
        new BatchStack(app, "BatchStack", stackProps, vpcStack.getVpc());
        if(DEPLOY_SLACK_LAMBDA) {
            new JobNotificationLambdaStack(app, "JobNotificationStack", stackProps, SLACK_HOOK_URL, SLACK_CHANNEL_NAME);
        }
        app.synth();
    }
}

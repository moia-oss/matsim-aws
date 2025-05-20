package io.moia.aws.infra;

import io.moia.aws.infra.stacks.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.IBucket;

public class Run {

    public static final Environment ENV = makeEnv("xxxxxxxxxxxx", "eu-central-1");

    public static final boolean DEPLOY_SLACK_LAMBDA = false;
    // put in your slack hook url
    public static final String SLACK_HOOK_URL = "https://hooks.slack.com/services/.....";
    // change to your slack channel name
    public static final String SLACK_CHANNEL_NAME = "general";

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
        new JobNotificationLambdaStack(app, "JobNotificationStack", stackProps, SLACK_HOOK_URL, SLACK_CHANNEL_NAME);
        app.synth();
    }
}

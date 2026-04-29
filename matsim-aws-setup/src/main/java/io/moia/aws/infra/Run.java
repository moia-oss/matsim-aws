package io.moia.aws.infra;

import io.moia.aws.infra.stacks.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.IBucket;

public class Run {

    static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }

    static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }

    static App buildApp(String account, String region, String iamPolicyCsv, boolean deploySlackLambda,
                        String slackHookUrl, String slackChannelName) {
        App app = new App();
        StackProps stackProps = StackProps.builder().env(makeEnv(account, region)).build();

        VPCStack vpcStack = new VPCStack(app, "VpcStack", stackProps);
        S3Stack s3Stack = new S3Stack(app, "S3Stack", stackProps);

        IBucket inputBucket = s3Stack.getInputBucket();
        IBucket outputBucket = s3Stack.getOutputBucket();

        new IAMStack(app, "IAMStack", stackProps, inputBucket, outputBucket, PolicyStatementParser.parse(iamPolicyCsv));
        new ECRStack(app, "ECRStack", stackProps);
        BatchStack batchStack = new BatchStack(app, "BatchStack", stackProps, vpcStack.getImportableVpc());
        // Fn.importValue() tokens are opaque to CDK's dependency graph, so we declare
        // the dependency explicitly to ensure VpcStack is fully deployed before BatchStack.
        batchStack.addDependency(vpcStack);
        if (deploySlackLambda) {
            new JobNotificationStack(app, "JobNotificationStack", stackProps, slackHookUrl, slackChannelName);
        }
        return app;
    }

    public static void main(final String[] args) {
        App app = buildApp(
                requireEnv("AWS_ACCOUNT"),
                requireEnv("REGION"),
                System.getenv("IAM_POLICY_CSV"),
                Boolean.parseBoolean(System.getenv("DEPLOY_SLACK_LAMBDA")),
                System.getenv("SLACK_HOOK_URL"),
                System.getenv("SLACK_CHANNEL_NAME")
        );
        app.synth();
    }
}

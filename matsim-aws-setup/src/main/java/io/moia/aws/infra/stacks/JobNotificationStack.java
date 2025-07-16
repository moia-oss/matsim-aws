package io.moia.aws.infra.stacks;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class JobNotificationStack extends Stack {

    public JobNotificationStack(Construct scope, final String name, StackProps stackProps,
                                String slackHookUrl, String channelName) {
        super(scope, name, stackProps);
        new JobNotificationConstruct(this, name, slackHookUrl, channelName);
    }

    private static class JobNotificationConstruct extends Construct {

        public JobNotificationConstruct(final Construct parent, final String name, String slackHookUrl, String channelName) {
            super(parent, name);


            PolicyStatement policyStatement = new PolicyStatement();
            policyStatement.addActions("batch:DescribeJobs");
            policyStatement.addResources("*");


            Function functionOne = new Function(this, "BatchJobNotification", FunctionProps.builder()
                    .runtime(Runtime.PYTHON_3_13)
                    .functionName("MATSimJobNotifier")
                    .code(Code.fromAsset("./notificationLambda/"))
                    .handler("JobNotification.handler")
                    .environment(Map.of(
                            "SLACK_HOOK_URL", slackHookUrl,
                            "SLACK_TEAM_MENTION", "",
                            "SLACK_COLOR", "#6ECADC",
                            "SLACK_CHANNEL", channelName
                    ))
                    .initialPolicy(List.of(policyStatement))
                    .build());


            Rule.Builder.create(this, "BatchNotificationEventRule")
                    .description("Cloudwatch event that triggers the notification.")
                    .ruleName("BatchJobNotificationRule")
                    .eventPattern(
                            EventPattern.builder()
                                    .source(List.of("aws.batch"))
                                    .detailType(List.of("Batch Job State Change"))
                                    .build()
                    )
                    .targets(List.of(new LambdaFunction(functionOne)))
                    .build();
        }
    }
}

package io.moia.aws.infra.stacks;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

public class IAMStack extends Stack {

    public static final String MATSIM_ROLE_ARN_EXPORT = "matsimRoleArn";
    public static final String MATSIM_BATCH_ROLE = "MatsimBatchRole";

    public IAMStack(Construct scope, final String name, StackProps stackProps,
                    IBucket inputBucket, IBucket outputBucket, PolicyStatement... statements) {
        super(scope, name, stackProps);
        new IAMConstruct(this, name, inputBucket, outputBucket, statements);
    }

    private static class IAMConstruct extends Construct {
        public IAMConstruct(final Construct parent, final String name,
                            IBucket inputBucket, IBucket outputBucket,
                            PolicyStatement... statements) {
            super(parent, name);

            Object flag = this.getNode().tryGetContext("useExistingIamRole");
            boolean useExisting = Boolean.parseBoolean((String) flag);

            IRole matsimRole;
            if(useExisting) {
                // Import existing role instead of creating it
                matsimRole = Role.fromRoleName(this, "MatsimRole", MATSIM_BATCH_ROLE);
            } else {
                RoleProps roleProps = RoleProps.builder()
                        .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                        .description("Role assumed in batch job")
                        .roleName(MATSIM_BATCH_ROLE)
                        .build();

                 matsimRole = new Role(this, "MatsimRole", roleProps);

                for (PolicyStatement statement : statements) {
                    ((Role) matsimRole).addToPolicy(statement);
                }
            }

            inputBucket.grantReadWrite(matsimRole);
            outputBucket.grantReadWrite(matsimRole);

            CfnOutput.Builder.create(this, MATSIM_ROLE_ARN_EXPORT)
                    .value(matsimRole.getRoleArn())
                    .exportName(MATSIM_ROLE_ARN_EXPORT)
                    .build();
        }
    }
}

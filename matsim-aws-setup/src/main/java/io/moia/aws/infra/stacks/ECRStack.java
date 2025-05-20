package io.moia.aws.infra.stacks;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ecr.LifecycleRule;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.TagStatus;
import software.constructs.Construct;

import java.util.List;


/**
 * Defines the ECR stack.
 */
public class ECRStack extends Stack {

    private static final String MATSIM_JOBS_REPO_NAME = "matsim-jobs-repo";

    /** see https://docs.aws.amazon.com/AmazonECR/latest/userguide/LifecyclePolicies.html
     * "With countType = imageCountMoreThan, images are sorted from youngest to oldest based on pushed_at_time
     *  and then all images greater than the specified count are expired."
     */
    private final static int dockerImageRetentionCount = 10;

    public ECRStack(Construct scope, final String name, StackProps stackProps) {
        super(scope, name, stackProps);
        new ECRConstruct(this, name);
    }

    private static class ECRConstruct extends Construct {
        public ECRConstruct(final Construct parent, final String name) {
            super(parent, name);

            Repository matsimRepo = Repository.Builder.create(this, "MatsimRepo")
                    .repositoryName(MATSIM_JOBS_REPO_NAME)
                    .removalPolicy(RemovalPolicy.RETAIN)
                    .build();

            matsimRepo.addLifecycleRule(LifecycleRule
                    .builder()
                    .description("retain image with tag 'latest'")
                            .tagPrefixList(List.of("latest"))
                            .maxImageCount(100)
                    .build()
            );
            matsimRepo.addLifecycleRule(LifecycleRule
                    .builder()
                    .description("remove old images")
                            .tagStatus(TagStatus.UNTAGGED)
                            .maxImageCount(dockerImageRetentionCount)
                    .build()
            );

            CfnOutput.Builder.create(this, "ecrMatsimJobsArn")
                    .value(matsimRepo.getRepositoryArn())
                    .exportName("ecrMatsimJobsArn")
                    .build();
        }
    }
}


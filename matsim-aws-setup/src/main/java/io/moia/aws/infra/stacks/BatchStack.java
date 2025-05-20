package io.moia.aws.infra.stacks;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.batch.AllocationStrategy;
import software.amazon.awscdk.services.batch.JobQueue;
import software.amazon.awscdk.services.batch.ManagedEc2EcsComputeEnvironment;
import software.amazon.awscdk.services.batch.ManagedEc2EcsComputeEnvironmentProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.List;

/**
 * Defines the batch cluster.
 */
public class BatchStack extends Stack {

    public static final String MATSIM_JOB_QUEUE_ON_DEMAND = "MATSimJobQueueOnDemand";

    public BatchStack(Construct scope, final String name, StackProps stackProps, Vpc vpc) {
        super(scope, name, stackProps);
        new BatchConstruct(this, name, vpc);
    }

    private static class BatchConstruct extends Construct {
        public BatchConstruct(final Construct parent, final String name, IVpc vpc) {
            super(parent, name);

            LaunchTemplate matsimLaunchTemplate = LaunchTemplate.Builder.create(this, "matsimLaunchTemplate")
                    .launchTemplateName("matsimLaunchTemplate")
                    .requireImdsv2(true)
                    // see https://aws.amazon.com/premiumsupport/knowledge-center/batch-ebs-volumes-launch-template/
                    // volume size is in GBytes
                    .blockDevices(
                            List.of(
                                    BlockDevice.builder()
                                            .deviceName("/dev/xvda")
                                            .volume(BlockDeviceVolume.ebs(512,
                                                    EbsDeviceOptions.builder().volumeType(EbsDeviceVolumeType.GP2).build()
                                            ))
                                            .build()
                            )
                    )
                    .build();

            ManagedEc2EcsComputeEnvironmentProps environmentProps = ManagedEc2EcsComputeEnvironmentProps
                    .builder()
                    .vpc(vpc)
                    .useOptimalInstanceClasses(false)
                    .launchTemplate(matsimLaunchTemplate)
                    .instanceClasses(List.of(InstanceClass.M7G, InstanceClass.R7G))
                    .allocationStrategy(AllocationStrategy.BEST_FIT_PROGRESSIVE)
                    .maxvCpus(512)
                    .minvCpus(0).build();

            ManagedEc2EcsComputeEnvironment computeEnvironment = new ManagedEc2EcsComputeEnvironment(this,
                    "MATSimComputeEnvironmentOnDemand", environmentProps);

            JobQueue matSimJobQueueOnDemand = JobQueue.Builder.create(this, MATSIM_JOB_QUEUE_ON_DEMAND)
                    .jobQueueName(MATSIM_JOB_QUEUE_ON_DEMAND) // <-- Explicit name here
                    .build();

            matSimJobQueueOnDemand.addComputeEnvironment(computeEnvironment, 1);
        }
    }
}
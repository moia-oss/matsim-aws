package io.moia.aws.infra.stacks;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.batch.AllocationStrategy;
import software.amazon.awscdk.services.batch.CfnComputeEnvironment;
import software.amazon.awscdk.services.batch.EcsMachineImage;
import software.amazon.awscdk.services.batch.EcsMachineImageType;
import software.amazon.awscdk.services.batch.JobQueue;
import software.amazon.awscdk.services.batch.ManagedEc2EcsComputeEnvironment;
import software.amazon.awscdk.services.batch.ManagedEc2EcsComputeEnvironmentProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.AmiHardwareType;
import software.amazon.awscdk.services.ecs.EcsOptimizedImage;
import software.constructs.Construct;

import java.util.List;

/**
 * Defines the batch cluster.
 */
public class BatchStack extends Stack {

    public static final String MATSIM_JOB_QUEUE_ON_DEMAND = "MATSimJobQueueOnDemand";

    public BatchStack(Construct scope, final String name, StackProps stackProps, IVpc vpc) {
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
                                                    EbsDeviceOptions.builder().volumeType(EbsDeviceVolumeType.GP3).build()
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
                    .instanceClasses(List.of(InstanceClass.M8G, InstanceClass.M7G, InstanceClass.R7G, InstanceClass.R8G))
                    .images(List.of(
                            EcsMachineImage.builder()
                                    .image(EcsOptimizedImage.amazonLinux2023(AmiHardwareType.ARM))
                                    .imageType(EcsMachineImageType.ECS_AL2023)
                                    .build()
                    ))
                    .allocationStrategy(AllocationStrategy.BEST_FIT_PROGRESSIVE)
                    .maxvCpus(2048)
                    .minvCpus(0).build();

            ManagedEc2EcsComputeEnvironment computeEnvironment = new ManagedEc2EcsComputeEnvironment(this,
                    "MATSimComputeEnvironmentOnDemand", environmentProps);

            // AWS Batch applies a new launch template version only during an infrastructure update of the compute
            // environment; with the default ($Default) it keeps the version from the time the compute environment was
            // created. Referring to the latest version makes a changed launch template trigger such an update.
            ((CfnComputeEnvironment) computeEnvironment.getNode().getDefaultChild()).addPropertyOverride(
                    "ComputeResources.LaunchTemplate.Version", matsimLaunchTemplate.getLatestVersionNumber());

            JobQueue matSimJobQueueOnDemand = JobQueue.Builder.create(this, MATSIM_JOB_QUEUE_ON_DEMAND)
                    .jobQueueName(MATSIM_JOB_QUEUE_ON_DEMAND) // <-- Explicit name here
                    .build();

            matSimJobQueueOnDemand.addComputeEnvironment(computeEnvironment, 1);
        }
    }
}
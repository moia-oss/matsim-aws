package io.moia.aws.infra;

import io.moia.aws.infra.stacks.BatchStack;
import io.moia.aws.infra.stacks.VPCStack;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunTest {

    private static final String TEST_ACCOUNT = "123456789012";
    private static final String TEST_REGION = "eu-central-1";

    private static App buildTestApp() {
        return Run.buildApp(TEST_ACCOUNT, TEST_REGION, null, false, null, null);
    }

    private static VPCStack vpcStack(App app) {
        return (VPCStack) app.getNode().findChild("VpcStack");
    }

    private static BatchStack batchStack(App app) {
        return (BatchStack) app.getNode().findChild("BatchStack");
    }

    // Without a fail-fast guard, a missing REGION env var silently produces an environment-agnostic
    // CDK app, causing Fn::GetAZs tokens in subnet AZs instead of hardcoded strings (see below).
    @Test
    void requireEnv_throwsWhenMissing() {
        assertThrows(IllegalStateException.class, () -> Run.requireEnv("__MISSING_VAR_XYZ__"));
    }

    @Test
    void requireEnv_returnsValueWhenSet() {
        assertDoesNotThrow(() -> Run.requireEnv("PATH")); // PATH is always present
    }

    // BatchStack imports subnet IDs and AZs from VpcStack via Fn::ImportValue. Those export names
    // are referenced by name in deployed stacks: renaming or removing them would break any existing
    // deployment that still has the old BatchStack template in CloudFormation.
    @Test
    void vpcStack_exportsAllPrivateSubnetIds() {
        VPCStack vpc = vpcStack(buildTestApp());
        Template template = Template.fromStack(vpc);
        int count = vpc.getVpc().getPrivateSubnets().size();

        for (int i = 0; i < count; i++) {
            template.hasOutput("privateSubnetId" + i,
                    Map.of("Export", Map.of("Name", VPCStack.exportPrivateSubnetId(i))));
        }
    }

    @Test
    void vpcStack_exportsAllPrivateSubnetAzs() {
        VPCStack vpc = vpcStack(buildTestApp());
        Template template = Template.fromStack(vpc);
        int count = vpc.getVpc().getPrivateSubnets().size();

        for (int i = 0; i < count; i++) {
            template.hasOutput("privateSubnetAz" + i,
                    Map.of("Export", Map.of("Name", VPCStack.exportPrivateSubnetAz(i))));
        }
    }

    // When REGION is null, CDK produces an environment-agnostic app and cannot resolve AZs at synth
    // time. It falls back to Fn::GetAZs intrinsics, which CloudFormation resolves at deploy time in
    // alphabetical order. If the account's actual AZ order differs, CloudFormation sees a change to
    // the subnet AvailabilityZone property — a replace-only field — and tries to recreate all
    // subnets, failing because the old ones still exist with the same CIDRs.
    @Test
    void vpcStack_subnetAzsAreHardcodedStrings() {
        Template template = Template.fromStack(vpcStack(buildTestApp()));

        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
                Map.of("AvailabilityZone", Map.of("Fn::Select", Match.anyValue())), 0);
        template.resourcePropertiesCountIs("AWS::EC2::Subnet",
                Map.of("AvailabilityZone", Map.of("Fn::GetAZs", Match.anyValue())), 0);
    }

    // BatchStack imports VPC subnet IDs via Fn::ImportValue, which is opaque to CDK's dependency
    // graph. Without an explicit addDependency(), CDK deploys VpcStack and BatchStack in parallel.
    // On a first deploy BatchStack can start before VpcStack has created its exports, causing
    // "No export named matsim-private-subnet-id-N found" and a BatchStack rollback.
    @Test
    void batchStack_dependsOnVpcStack() {
        App app = buildTestApp();
        Stack vpc = vpcStack(app);
        Stack batch = batchStack(app);

        boolean dependsOnVpc = batch.getDependencies().stream()
                .anyMatch(dep -> dep.getStackName().equals(vpc.getStackName()));
        assertTrue(dependsOnVpc, "BatchStack must declare an explicit dependency on VpcStack");
    }

    // Resource tags (e.g. for cost allocation) must reach the resources that cause the costs: the EC2 instances
    // and EBS volumes launched by AWS Batch, and the S3 buckets.

    private static final Map<String, String> RESOURCE_TAGS = Map.of("Owner", "test-team", "Service", "matsim");

    private static App buildTaggedTestApp() {
        return Run.buildApp(TEST_ACCOUNT, TEST_REGION, null, false, null, null, RESOURCE_TAGS);
    }

    private static Template stackTemplate(App app, String stackName) {
        return Template.fromStack((Stack) app.getNode().findChild(stackName));
    }

    @Test
    void resourceTags_reachBatchInstancesAndVolumes() {
        Template template = stackTemplate(buildTaggedTestApp(), "BatchStack");

        for (String resourceType : List.of("instance", "volume")) {
            template.hasResourceProperties("AWS::EC2::LaunchTemplate", Map.of("LaunchTemplateData",
                    Map.of("TagSpecifications", Match.arrayWith(List.of(Map.of(
                            "ResourceType", resourceType,
                            "Tags", Match.arrayWith(List.of(
                                    Map.of("Key", "Owner", "Value", "test-team"),
                                    Map.of("Key", "Service", "Value", "matsim")))))))));
        }
        template.hasResourceProperties("AWS::Batch::ComputeEnvironment", Map.of("ComputeResources",
                Map.of("Tags", Map.of("Owner", "test-team", "Service", "matsim"))));
    }

    @Test
    void resourceTags_reachBuckets() {
        Template template = stackTemplate(buildTaggedTestApp(), "S3Stack");
        int buckets = template.findResources("AWS::S3::Bucket").size();

        assertTrue(buckets > 0);
        template.resourcePropertiesCountIs("AWS::S3::Bucket",
                Map.of("Tags", Match.arrayWith(List.of(Map.of("Key", "Owner", "Value", "test-team")))), buckets);
    }

    @Test
    void resourceTags_noTagsByDefault() {
        Template template = stackTemplate(buildTestApp(), "S3Stack");

        template.resourcePropertiesCountIs("AWS::S3::Bucket",
                Map.of("Tags", Match.arrayWith(List.of(Map.of("Key", "Owner", "Value", Match.anyValue())))), 0);
        stackTemplate(buildTestApp(), "BatchStack").resourcePropertiesCountIs("AWS::Batch::ComputeEnvironment",
                Map.of("ComputeResources", Map.of("Tags", Match.anyValue())), 0);
    }

    /**
     * The root volume of the Batch instances is gp3: about 20 % cheaper than gp2 at the same size, with a baseline of
     * 3,000 IOPS and 125 MiB/s independent of the size.
     */
    @Test
    void batchInstances_useGp3RootVolume() {
        stackTemplate(buildTestApp(), "BatchStack").hasResourceProperties("AWS::EC2::LaunchTemplate",
                Map.of("LaunchTemplateData", Map.of("BlockDeviceMappings", Match.arrayWith(List.of(Map.of(
                        "DeviceName", "/dev/xvda",
                        "Ebs", Match.objectLike(Map.of("VolumeSize", 512, "VolumeType", "gp3"))))))));
    }

    /**
     * AWS Batch applies a new launch template version only during an infrastructure update of the compute environment;
     * with $Default it keeps the version from the time the compute environment was created. The compute environment
     * therefore refers to the latest version explicitly, so that a changed launch template (e.g. the volume type)
     * reaches new instances of an existing deployment.
     */
    @Test
    void batchComputeEnvironment_usesLatestLaunchTemplateVersion() {
        Template template = stackTemplate(buildTestApp(), "BatchStack");
        String launchTemplate = template.findResources("AWS::EC2::LaunchTemplate").keySet().iterator().next();

        template.hasResourceProperties("AWS::Batch::ComputeEnvironment", Map.of(
                "ReplaceComputeEnvironment", false,
                "ComputeResources", Match.objectLike(Map.of("LaunchTemplate", Map.of(
                        "LaunchTemplateId", Map.of("Ref", launchTemplate),
                        "Version", Map.of("Fn::GetAtt", List.of(launchTemplate, "LatestVersionNumber")))))));
    }
}

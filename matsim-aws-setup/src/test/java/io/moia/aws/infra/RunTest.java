package io.moia.aws.infra;

import io.moia.aws.infra.stacks.BatchStack;
import io.moia.aws.infra.stacks.VPCStack;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

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
}

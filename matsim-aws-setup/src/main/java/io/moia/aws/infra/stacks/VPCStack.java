package io.moia.aws.infra.stacks;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VPCStack extends Stack {

    private static Vpc matsimVPC;

    // Stable CloudFormation export name for the VPC ID.
    // Intentionally unchanged from the existing export so any external consumers
    // that already reference "mainVpcId" continue to work.
    public static final String EXPORT_VPC_ID = "mainVpcId";

    // Stable export name patterns for private subnet IDs and AZs.
    // Index matches the order returned by Vpc.getPrivateSubnets().
    private static String exportPrivateSubnetId(int index) {
        return "matsim-private-subnet-id-" + index;
    }

    private static String exportPrivateSubnetAz(int index) {
        return "matsim-private-subnet-az-" + index;
    }

    public VPCStack(Construct scope, final String name, StackProps stackProps) {
        super(scope, name, stackProps);
        new VPCConstruct(this, name);
    }

    public Vpc getVpc() {
        return matsimVPC;
    }

    /**
     * Returns an IVpc whose subnet IDs and AZs are resolved via Fn.importValue()
     * on the stable CloudFormation export names declared by this stack.
     *
     * Use this method instead of getVpc() when passing the VPC to another stack.
     * Fn.importValue() tokens do not trigger implicit CDK cross-stack exports,
     * so the export names remain stable across CDK version upgrades.
     */
    public IVpc getImportableVpc() {
        int subnetCount = matsimVPC.getPrivateSubnets().size();

        List<String> privateSubnetIds = new ArrayList<>();
        List<String> availabilityZones = new ArrayList<>();
        for (int i = 0; i < subnetCount; i++) {
            privateSubnetIds.add(Fn.importValue(exportPrivateSubnetId(i)));
            availabilityZones.add(Fn.importValue(exportPrivateSubnetAz(i)));
        }

        return Vpc.fromVpcAttributes(this, "ImportableVpc",
                VpcAttributes.builder()
                        .vpcId(Fn.importValue(EXPORT_VPC_ID))
                        .availabilityZones(availabilityZones)
                        .privateSubnetIds(privateSubnetIds)
                        .build());
    }

    private static class VPCConstruct extends Construct {

        public VPCConstruct(final Construct parent, final String name) {
            super(parent, name);

            Map<String, GatewayVpcEndpointOptions> s3 = Map.of(
                    "s3",
                    GatewayVpcEndpointOptions.builder().service(GatewayVpcEndpointAwsService.S3).build()
            );

            VpcProps vpcProps = VpcProps.builder().natGateways(1).gatewayEndpoints(s3).build();
            matsimVPC = new Vpc(this, "MatsimVPC", vpcProps);

            // This is required by PE and making this setting is not possible on the high-level CDK construct
            // because of that we are casting the public subnets to lower level CfnSubnet
            for (ISubnet subnet : matsimVPC.getPublicSubnets()) {
                ((CfnSubnet) subnet.getNode().getDefaultChild()).setMapPublicIpOnLaunch(false);
            }

            // Existing exports — kept with `this` (inner construct) as parent to
            // preserve their CloudFormation logical IDs.
            CfnOutput.Builder.create(this, "mainVpcId")
                    .value(matsimVPC.getVpcId())
                    .exportName(EXPORT_VPC_ID)
                    .build();

            CfnOutput.Builder.create(this, "mainVpcArn")
                    .value(matsimVPC.getVpcArn())
                    .exportName("mainVpcArn")
                    .build();

            // New stable private subnet exports, attached to `parent` (the VPCStack)
            // so their logical IDs are simple and predictable.
            List<? extends ISubnet> privateSubnets = matsimVPC.getPrivateSubnets();
            for (int i = 0; i < privateSubnets.size(); i++) {
                ISubnet subnet = privateSubnets.get(i);
                CfnOutput.Builder.create(parent, "privateSubnetId" + i)
                        .value(subnet.getSubnetId())
                        .exportName(exportPrivateSubnetId(i))
                        .build();
                CfnOutput.Builder.create(parent, "privateSubnetAz" + i)
                        .value(subnet.getAvailabilityZone())
                        .exportName(exportPrivateSubnetAz(i))
                        .build();
            }
        }
    }
}

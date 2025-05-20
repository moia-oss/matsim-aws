package io.moia.aws.infra.stacks;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.Map;

public class VPCStack extends Stack {

    private static Vpc matsimVPC;

    public VPCStack(Construct scope, final String name, StackProps stackProps) {
        super(scope, name, stackProps);
        new VPCConstruct(this, name);
    }

    public Vpc getVpc() {
        return matsimVPC;
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

            CfnOutput.Builder.create(this, "mainVpcId")
                    .value(matsimVPC.getVpcId())
                    .exportName("mainVpcId")
                    .build();

            CfnOutput.Builder.create(this, "mainVpcArn")
                    .value(matsimVPC.getVpcArn())
                    .exportName("mainVpcArn")
                    .build();
        }
    }
}
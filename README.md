# MATSim - AWS
AWS (amazon web services) setup for MATSim simulations.

Provides generic infrastructure as code templates
to setup MATSim simulations to run as AWS batch jobs.

Includes:
- S3 buckets for input (including executable jars) and output
- IAM identity roles with the appropriate access rights
- ECR repository to hold docker/podman images for execution
- VPC setup
- Batch setup to start individual jobs including starting and 
shutting down appropriately sized machines
- (optionally) a job notification lambda function that notifies 
the user about the end of a job via a predefined slack workspace 
and channel

# Requirements
- your own AWS account
- AWS cdk installed for deploying the AWS resources
- podman/docker for building the job image
- AWS cli installed for pushing the docker image to ECR
- a packaged executable jar (i.e., shaded via maven)

# Steps
## Deploy AWS App

Execute maven `compile` goal. Check if there is

Run:

`cdk boostrap`

`cdk deploy --all`

## Build image and push to ECR
Use deployMatsimImage.sh for building and pushing the job image once 
the ECR repository has been setup.

# Useful commands

* `mvn package`     compile and run tests
* `cdk ls`          list all stacks in the app
* `cdk synth`       emits the synthesized CloudFormation template
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk docs`        open CDK documentation


# DISCLAIMER:
The code is provided as is. There is no warranty about 
the correct usage of AWS resources. Any costs incurring in
the user's account should be monitored closely and are in the 
whole responsibility of the user.

! Any deployment of this app may alter your AWS setup unintentionally !

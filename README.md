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
- Java + Maven
- your own AWS account
- AWS cdk installed for deploying the AWS resources
- podman/docker for building the job image
- AWS cli installed for pushing the docker image to ECR
- a packaged executable jar (i.e., shaded via maven)

# Steps
## Deploy AWS App

Go to the `matsim-aws-setup` maven module.
Execute maven `compile` goal.
Run:

`cdk boostrap`

`cdk deploy --all`

Check if there is a `cdk.out` folder under `matsim-aws-setup`

## Build image and push to ECR
Use `deployMatsimImage.sh` for building and pushing the job image once 
the ECR repository has been setup.

## Run Example

The `scenarios` folder contains the `equil` example scenario from MATSim original repo.
Within the `matsim-aws-setup` module, there is the `io.moia.aws.run` package that shows
how to get your first simulation running.

First, (with correct AWS credentials in your environment) run the `PrepareInput` class,
which simply uploads the required scenario files to your newly created S3 input bucket.
Make sure to set your account in the `bucket` variable in the header of the class.

Next, you need to package the maven module into an executable jar, such that it contains the `RunEquil`
main class. You can use the `updateJar.sh` script provided here to run the package command and
update the resulting jar into the correct input bucket path.

Once the input and jar are uploaded you need to define a AWS batch job definition. The job definition
acts like a template for defining how a job should be run.
The definition defines various parameters, such as the batch job queue, the input/output buckets,
Main class, etc. Run the `EquilExampleJobDefinition` (with correct AWS credentials set and account name
set in the header of the class) to register the definition in your account.

Now you can run the `EquilExampleJobSubmission`class to actually submit your job. A link to the
AWS batch job will be printed to the console. The output will be synced to your output bucket.


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

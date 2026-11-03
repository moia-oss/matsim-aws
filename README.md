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

The published `matsim-aws-setup` Maven artifact contains only AWS infrastructure and Batch submission utilities. MATSim itself is used by the Equil example project under `examples/equil`.

# Steps

## Setup Environment
Fill in the environment variables in the `environment.env` 
file. You will need at least the AWS account number and your
desired region. This environment will act as a single source 
of truth throughout the setup.

## Set your AWS credentials in the System environment
Make sure that your credentials are available as environment variables.

As an alternative, if your organization supports it, use AWS SSO as described [here](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html).
Be careful, the region of the SSO might be different from the region of your target account!
You can see the parameters you need after logging in, by selecting the "Access Keys" link next to your account.

Use `aws sso login --profile my-profile` before running the deploy script.

## Deploy AWS App

Run the `1_deployAWSInfrastructure.sh` script. This will
- go to the `matsim-aws-setup` maven module.
- execute maven `compile` goal.
- Run:
  - `cdk bootstrap`
  - `cdk deploy --all`

  
Afterwards, check if there is a `cdk.out` folder under `matsim-aws-setup`

## Build image and push to ECR
Use `2_deployMatsimImage.sh` for building and pushing the job image once 
the ECR repository has been setup.

## Run Example

The `scenarios` folder contains the `equil` example scenario from the MATSim original repository.
The runnable MATSim example lives in the separate Maven project `examples/equil`; the AWS setup library remains in `matsim-aws-setup`.

First, with correct AWS credentials and the `environment.env` variables in your environment, build the example jar:

```bash
mvn -f pom.xml -pl examples/equil -am clean package -DskipTests=true
```

Then upload the required scenario files to the input bucket:

```bash
java -cp examples/equil/target/equil.jar io.moia.aws.run.example.equil.PrepareInput
```

Upload the executable example jar to the `jars/equil.jar` key expected by the job definition:

```bash
./3_updateJar.sh
```

Register the AWS Batch job definition:

```bash
java -cp examples/equil/target/equil.jar io.moia.aws.run.example.equil.EquilExampleJobDefinition
```

Submit the example job:

```bash
java -cp examples/equil/target/equil.jar io.moia.aws.run.example.equil.EquilExampleJobSubmission
```

A link to the AWS Batch job is printed to the console. The output is synced to the configured output bucket.


# Useful commands

* `mvn -f matsim-aws-setup/pom.xml test`     compile and test the AWS setup artifact
* `mvn -f pom.xml -pl examples/equil -am package -DskipTests=true`     build the Equil example jar
* `cdk ls`          list all stacks in the app
* `cdk synth`       emits the synthesized CloudFormation template
* `cdk deploy`      deploy this stack to your default AWS account/region
* `cdk diff`        compare deployed stack with current state
* `cdk docs`        open CDK documentation

## Run Metadata

At the end of each job, `run.sh` writes a `_run_metadata.json` file to the job's S3 output prefix:

```
s3://{output-bucket}/{OUTPUT_SCENARIO}/{JOB_NAME}/_run_metadata.json
```

The file always contains:

| Field | Value |
|---|---|
| `jobName` | The AWS Batch job name |
| `outputPath` | The full S3 key prefix for this job's outputs |
| `completedAt` | ISO 8601 UTC timestamp of job completion |
| `status` | `"success"` or `"failed"` |

Additional fields can be injected at submission time by setting the `RUN_METADATA_EXTRA` environment variable (via container overrides) to a JSON fragment — comma-separated `"key": "value"` pairs without the enclosing braces:

```bash
RUN_METADATA_EXTRA='"triggeredBy": "alice", "githubRunId": "12345"'
```

## S3 Buckets

By default, the deployment creates two S3 buckets (input and output) and manages their configuration — including lifecycle rules — via CloudFormation. Re-running the deploy script on an existing setup is safe: CloudFormation updates bucket properties in-place without recreating them or affecting stored data.

If you want to bring your own pre-existing buckets and have CDK reference them without managing their configuration, set the `useExistingBuckets` context flag:

```bash
cdk deploy --all --context useExistingBuckets=true
```

When this flag is set, CDK imports the buckets by their expected names and does not create or modify them. Lifecycle rules and other bucket properties will not be applied.

## Automatic Cleanup of Failed Runs

The output S3 bucket includes a lifecycle rule (`DeleteFailedSimulationOutputs`) that automatically deletes the outputs of failed simulation runs. When a job exits with a non-zero code, `run.sh` tags every object in the job's output prefix with `SimulationStatus=failed`. The lifecycle rule deletes all tagged objects after a configurable retention period.

Configure the retention period at deploy time:

```bash
cdk deploy --context failedRunRetentionDays=14   # default: 7
```


## Cost Allocation Tags

To break down costs by team, project or scenario, you can tag the deployed resources and the submitted jobs.

**Infrastructure.** Set `RESOURCE_TAGS` in `environment.env` before deploying, e.g.

```
RESOURCE_TAGS="Owner=my-team,Service=matsim"
```

The tags are added to all resources of all stacks, including the S3 buckets, the Batch compute environment, and the
EC2 instances and EBS volumes that AWS Batch launches (via the launch template).

Adding or changing tags on an existing deployment **replaces the Batch compute environment**: CloudFormation cannot
update the tags of an `AWS::Batch::ComputeEnvironment` in place. All other resources are updated in place. Run
`cdk diff` first and deploy while no jobs are running or queued. A new deployment is not affected.

**Jobs.** Add tags per job with `JobSubmission.tag(key, value)` (and per job definition with `JobDefinition.tag`).
Job tags are propagated to the job's ECS task. `JobSubmission.owner(...)` sets the tag key `OWNER`.

**Billing.** Tags alone do not show up in cost reports. In your AWS organisation:

- Each tag key has to be activated as a *cost allocation tag* in the management (payer) account. Activation is not
  retroactive. Tag keys are case-sensitive, so use exactly the keys your organisation has activated.
- To attribute EC2 costs to individual jobs via their tags, *split cost allocation data* for Amazon ECS has to be
  enabled (Billing and Cost Management, Cost Management Preferences). The split costs are reported in the Cost and
  Usage Report (Data Exports).

Both settings are made in the billing console of the management account, not by this stack.

## Cost Report

`io.moia.aws.cost.CostReport` reads monthly costs per account from AWS Cost Explorer and writes them as CSV,
optionally grouped by the values of one tag:

```bash
cd matsim-aws-setup
mvn compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" io.moia.aws.cost.CostReport 2026-09-01 2026-11-01 Owner costs.csv
```

Arguments: start date, end date (exclusive), optional tag key, optional output file (default: standard output).
With a tag key, the report also prints per month the share of the cost that carries a value of the tag. A share of
(almost) 0 % means that the tag key is not activated as a cost allocation tag in your organisation, or that the
resources causing the costs are not tagged.

Notes:
- The credentials need `ce:GetCostAndUsage`. Each Cost Explorer API request is charged (currently USD 0.01).
- Cost Explorer does not show split cost allocation data (EC2 costs attributed to individual ECS tasks / Batch jobs).
  These are only reported in the Cost and Usage Report (Data Exports).

# DISCLAIMER:
The code is provided as is. There is no warranty about 
the correct usage of AWS resources. Any costs incurring in
the user's account should be monitored closely and are in the 
whole responsibility of the user.

! Any deployment of this app may alter your AWS setup unintentionally !

set -o allexport        # turn on “auto-export” of all variables you define
source environment.env  # read each line KEY=VALUE into your shell’s env
set +o allexport        # turn off auto-export again

cd matsim-aws-setup
mvn clean compile

cdk bootstrap

cdk deploy --all


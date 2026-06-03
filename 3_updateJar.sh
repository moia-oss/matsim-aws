#!/usr/bin/env bash
set -euo pipefail

set -o allexport
source ./environment.env
set +o allexport

BUCKET_NAME="s3://matsim-jobs-input-${AWS_ACCOUNT}"

mvn -f pom.xml -pl examples/equil -am clean package -DskipTests=true
aws s3 cp ./examples/equil/target/equil.jar "${BUCKET_NAME}/jars/equil.jar"

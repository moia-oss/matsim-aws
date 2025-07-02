#!/usr/bin/env bash

set -eu
set -o pipefail

JAR_NAME="${JAR_NAME}"
XMX="${XMX}"
JOB_NAME="${JOB_NAME}"
JOB_INPUT_BUCKET="${JOB_INPUT_BUCKET}"
JOB_OUTPUT_BUCKET="${JOB_OUTPUT_BUCKET}"


# Read comma-separated input directories into an array
IFS=',' read -r -a INPUT_DIRECTORIES <<< "$INPUT_DIRECTORIES"


if [ -z ${JOB_INPUT_BUCKET+x} ]; then
    echo "You need to specify a 'JOB_INPUT_BUCKET' environment variable (either in job definition or actual job)"
    exit
fi
if [ -z ${JOB_OUTPUT_BUCKET+x} ]; then
    echo "You need to specify a 'JOB_OUTPUT_BUCKET' environment variable (either in job definition or actual job)"
    exit
fi


#!/bin/bash
ARCH=$(uname -m | sed 's/x86_64/x86_64/;s/arm64/arm_64/')
OS=$(uname -s | sed 's/Darwin/darwin/;s/Linux/linux/')

MATSIM_TMPDIR=/tmp/matsim
OUTPUT_DIR=/tmp/output
SYNC_SEMAPHORE=/tmp/sync-semaphore
MATSIM_DONE_SEMAPHORE=/tmp/matsim-done-semaphore
echo "Xmx: ${XMX}"
echo "Job name: ${JOB_NAME}"

mkdir -p "${MATSIM_TMPDIR}"
mkdir -p "${OUTPUT_DIR}"

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

ls -lh ${SCRIPT_DIR}

SYNC_PATH="s3://${JOB_OUTPUT_BUCKET}/${OUTPUT_SCENARIO}/${JOB_NAME}"
${SCRIPT_DIR}/sync-loop.sh "${OUTPUT_DIR}" "${SYNC_PATH}" \
  "${SYNC_SEMAPHORE}" 180 &

cd "${SCRIPT_DIR}/"


echo "${SCRIPT_DIR}"

# sync all input directories
for directory in "${INPUT_DIRECTORIES[@]}"; do
  echo "Syncing from:" "s3://${JOB_INPUT_BUCKET}/${directory}"
  aws s3 sync --only-show-errors "s3://${JOB_INPUT_BUCKET}/${directory}" "./${directory}"
done


# assume JAR_NAME="io/moia/a.jar:io/moia/b.jar:other/x.jar"
IFS=':' read -r -a remote_jars <<< "$JAR_NAME"

downloaded_jars=()
for remote in "${remote_jars[@]}"; do
  echo "Downloading s3://${JOB_INPUT_BUCKET}/jars/${remote} …"
  aws s3 cp --only-show-errors \
      "s3://${JOB_INPUT_BUCKET}/jars/${remote}" \
      .
  # strip off any path – keep only the base filename
  localfile=$(basename "$remote")
  downloaded_jars+=( "$localfile" )
done


# now rebuild a colon‐separated list of the local filenames
JAR_CP=$(IFS=:; echo "${downloaded_jars[*]}")
echo "Local jars are: $JAR_CP"


ls

# shellcheck disable=SC2145
echo "Will start with the following MATSim Parameters: $@"
echo "$@" > "${OUTPUT_DIR}/matsim_parameters.txt"

aws s3 sync --only-show-errors "${OUTPUT_DIR}" "${SYNC_PATH}"


if [ -z ${AWS_BATCH_JOB_ARRAY_INDEX+x} ]; then
    echo "Single batch job"
    java -XX:+UseParallelGC \
      -XshowSettings:vm -XX:MinRAMPercentage=15 -Xmx${XMX} \
      -cp "${JAR_CP}" \
      -Djava.io.tmpdir="${MATSIM_TMPDIR}" \
      -Djava.library.path=/usr/lib/${ARCH}-${OS}-gnu/jni \
      ${MAIN_CLASS} \
      "$@"
else
    echo "Batch Array job"
    java -XX:+UseParallelGC \
      -XshowSettings:vm -XX:MinRAMPercentage=15 -Xmx${XMX} \
      -cp ${JAR_NAME} \
      -Djava.io.tmpdir="${MATSIM_TMPDIR}" \
      -Djava.library.path=/usr/lib/${ARCH}-${OS}-gnu/jni \
      ${MAIN_CLASS} \
      --batch-job-array-index "${AWS_BATCH_JOB_ARRAY_INDEX}" "$@"
fi
# we are using the traditional parallel GC - for noninteractive applications i.e. pure throughput it's still the best


aws s3 sync --only-show-errors "${OUTPUT_DIR}" "${SYNC_PATH}"

# make sure a full sync cycle was completed before we end the job
while [ "${MATSIM_DONE_SEMAPHORE}" -nt "${SYNC_SEMAPHORE}" ]; do
  SLEEP_FOR=60
  echo "Waiting for until sync to S3 has completed. Checking every ${SLEEP_FOR} seconds..."
  sleep ${SLEEP_FOR}
done

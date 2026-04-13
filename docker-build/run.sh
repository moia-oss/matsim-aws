#!/usr/bin/env bash

set -eu
set -o pipefail

JAR_NAME="${JAR_NAME}"
XMX="${XMX}"
JOB_NAME="${JOB_NAME}"
JOB_INPUT_BUCKET="${JOB_INPUT_BUCKET}"
JOB_OUTPUT_BUCKET="${JOB_OUTPUT_BUCKET}"



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

# Resolve a path entry to a full S3 URI: pass through if already s3://, else prepend input bucket.
resolve_s3_uri() {
  local entry="$1"
  if [[ "$entry" == s3://* ]]; then
    echo "$entry"
  else
    echo "s3://${JOB_INPUT_BUCKET}/${entry}"
  fi
}

# Strip s3://bucket-name/ to obtain the local relative path.
# e.g. s3://matsim-jobs-input-123456789/examples/equil/ -> examples/equil/
s3_uri_to_local_path() {
  local without_scheme="${1#s3://}"   # strip "s3://"
  echo "${without_scheme#*/}"         # strip "bucket-name/"
}

# Fetch all input paths.
# Each entry is either a relative path (resolved against JOB_INPUT_BUCKET) or a full s3:// URI.
# Trailing slash forces directory sync; otherwise auto-detects file vs. prefix via head-object.
IFS=',' read -r -a INPUT_PATHS_ARRAY <<< "${INPUT_PATHS:-}"
for entry in "${INPUT_PATHS_ARRAY[@]}"; do
  [[ -z "$entry" ]] && continue
  uri=$(resolve_s3_uri "$entry")
  local_path=$(s3_uri_to_local_path "$uri")
  if [[ "$entry" == */ ]]; then
    echo "Syncing directory from: ${uri}"
    mkdir -p "./${local_path}"
    aws s3 sync --only-show-errors "$uri" "./${local_path}"
  else
    without_scheme="${uri#s3://}"
    bucket="${without_scheme%%/*}"
    key="${without_scheme#*/}"
    if aws s3api head-object --bucket "$bucket" --key "$key" > /dev/null 2>&1; then
      echo "Copying file from: ${uri}"
      mkdir -p "$(dirname "./${local_path}")"
      aws s3 cp --only-show-errors "$uri" "./${local_path}"
    else
      echo "Syncing directory from: ${uri}"
      mkdir -p "./${local_path}"
      aws s3 sync --only-show-errors "$uri" "./${local_path}"
    fi
  fi
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


set +e
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
RET=$?
set -e

if [ "${RET}" -eq 0 ]; then STATUS="success"; else STATUS="failed"; fi

mkdir -p /tmp/output
EXTRA_FIELDS=""
if [ -n "${RUN_METADATA_EXTRA:-}" ]; then
    EXTRA_FIELDS=", ${RUN_METADATA_EXTRA}"
fi
cat > /tmp/output/_run_metadata.json <<EOF
{
  "jobName": "${JOB_NAME}",
  "outputPath": "${OUTPUT_SCENARIO}/${JOB_NAME}",
  "completedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "${STATUS}",
  "inputPaths": "${INPUT_PATHS:-}"${EXTRA_FIELDS}
}
EOF

aws s3 sync --only-show-errors "${OUTPUT_DIR}" "${SYNC_PATH}"

# Sentinel tag on _run_metadata.json (immediate observability, one API call)
aws s3api put-object-tagging \
    --bucket "${JOB_OUTPUT_BUCKET}" \
    --key "${OUTPUT_SCENARIO}/${JOB_NAME}/_run_metadata.json" \
    --tagging "TagSet=[{Key=SimulationStatus,Value=${STATUS}}]"

# Bulk-tag all output objects (for lifecycle rule storage cleanup, failed runs only)
if [ "${STATUS}" = "failed" ]; then
    aws s3api list-objects-v2 \
        --bucket "${JOB_OUTPUT_BUCKET}" \
        --prefix "${OUTPUT_SCENARIO}/${JOB_NAME}/" \
        --query "Contents[].Key" \
        --output text \
    | tr '\t' '\n' \
    | while read -r key; do
        [[ -z "$key" ]] && continue
        aws s3api put-object-tagging \
            --bucket "${JOB_OUTPUT_BUCKET}" \
            --key "${key}" \
            --tagging "TagSet=[{Key=SimulationStatus,Value=failed}]"
      done
fi

# make sure a full sync cycle was completed before we end the job
while [ "${MATSIM_DONE_SEMAPHORE}" -nt "${SYNC_SEMAPHORE}" ]; do
  SLEEP_FOR=60
  echo "Waiting for until sync to S3 has completed. Checking every ${SLEEP_FOR} seconds..."
  sleep ${SLEEP_FOR}
done

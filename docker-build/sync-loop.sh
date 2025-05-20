#!/usr/bin/env bash

# intention: run this script in background.
# the script runs continuously
# when MATSim has finished running the calling script waits until semaphore is newer than end time of MATSim
# this way we make sure that a full sync was performed after the simulation itself has finished

set -eu
set -o pipefail

if [ "$#" -ne 4 ]; then
    echo "Usage: this-script [input-dir] [target-s3-uri] [semaphore-file] [sleep-seconds]"
    exit 1
fi

SOURCE=$1
TARGET=$2
SEMAPHORE=$3
SLEEP_FOR=$4

while true; do
  aws s3 sync --only-show-errors "${SOURCE}" "${TARGET}"
  touch "${SEMAPHORE}"
  sleep "${SLEEP_FOR}"
done

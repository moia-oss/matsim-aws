#!/bin/zsh
APP_DIR="/usr/share/matsimjobs/"
CURDIR=$(pwd)
/opt/podman/bin/podman build -t matsim-job:latest .
/opt/podman/bin/podman run --rm -v $CURDIR/data:${APP_DIR}data -e JAR_NAME=$JAR_NAME -e JOB_NAME=$JOB_NAME -e SCENARIO=$SCENARIO -e OUTPUT_SCENARIO=$OUTPUT_SCENARIO -e WEEK_VALUE=$WEEK_VALUE -e RUN_MODE=$RUN_MODE -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID -e XMX=$XMX -it matsim-job:latest $@

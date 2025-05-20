#!/usr/bin/env bash
set -eu
set -o pipefail

# Install packages
export DEBIAN_FRONTEND=noninteractive

apt-get update -qq
apt-get upgrade -qq --assume-yes
# some custom extensions to MATSim require libglpk-java
apt install openjdk-21-jdk --assume-yes
apt-get install -qq --assume-yes gzip libglpk-java curl unzip

# Get the architecture of the current machine
ARCH=$(uname -m)

if [ "$ARCH" = "x86_64" ]; then
  # Commands for AMD64 architecture
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
elif [ "$ARCH" = "aarch64" ]; then
  # Commands for ARM64 architecture
  curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
else
  echo "Architecture not recognized";
  exit 1;
fi

unzip awscliv2.zip
./aws/install

apt-get -qq clean

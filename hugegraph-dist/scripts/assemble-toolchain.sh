#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -euo pipefail

# Do not restore or synthesize macOS AppleDouble metadata in release archives.
export COPYFILE_DISABLE=1

if [[ $# -ne 2 || ! -f "$1/pom.xml" || ! "$2" =~ ^[[:alnum:]._-]+$ ]]; then
    echo "Usage: $0 <toolchain-root> <version>" >&2
    exit 1
fi

root=$(cd "$1" && pwd)
version=$2
release_name="apache-hugegraph-toolchain-$version"
mkdir -p "$root/target"
staging=$(mktemp -d "$root/target/toolchain-stage.XXXXXX")
trap 'rm -rf -- "$staging"' EXIT
release="$staging/$release_name"
mkdir "$release"

# Use each module's distributable archive, including Hubble's runtime-data
# exclusions. Never move or reuse a release directory that may have been run.
for module in hubble loader tools; do
    archive="$root/hugegraph-$module/target/apache-hugegraph-$module-$version.tar.gz"
    [[ -f "$archive" ]]
    tar -xzf "$archive" --exclude='._*' --exclude='*/._*' -C "$release"
done
cp -R "$root/hugegraph-dist/release-docs/." "$release/"
tar -czf "$root/target/$release_name.tar.gz" \
    --exclude='._*' --exclude='*/._*' -C "$staging" "$release_name"
printf 'Toolchain archive: %s\n' "$root/target/$release_name.tar.gz"
